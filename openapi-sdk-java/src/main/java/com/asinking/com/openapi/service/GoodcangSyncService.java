package com.asinking.com.openapi.service;

import com.asinking.com.openapi.dto.response.SaleStatSyncResponse;
import com.asinking.com.openapi.entity.*;
import com.asinking.com.openapi.mapper.mp.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HashMap;

/**
 * 谷仓入库单同步服务：列表 + 详情（中转明细的 product_sku）。
 */
@Service
public class GoodcangSyncService {

    private final GoodcangClient client;
    private final GoodcangGrnListMapper grnListMapper;
    private final GoodcangGrnDetailMapper grnDetailMapper;
    private final GoodcangWarehouseMapper warehouseMapper;

    /** 构造同步服务，注入客户端及各 Mapper */
    public GoodcangSyncService(GoodcangClient client,
                               GoodcangGrnListMapper gLMapper,
                               GoodcangGrnDetailMapper gDMapper,
                               GoodcangWarehouseMapper whMapper) {
        this.client = client;
        this.grnListMapper = gLMapper;
        this.grnDetailMapper = gDMapper;
        this.warehouseMapper = whMapper;
    }

    /** 同步入库单列表及明细，按日期范围分页拉取并 upsert */
    @Transactional
    public SaleStatSyncResponse syncGrn(String from, String to) throws Exception {
        int totalList = 0, totalDetail = 0;
        int page = 1;

        while (true) {
            Map<String, Object> resp = client.getGrnList(from, to, page, 200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
            if (data == null || data.isEmpty()) break;

            for (Map<String, Object> item : data) {
                String code = String.valueOf(item.get("receiving_code"));
                if (code == null || code.isEmpty()) continue;

                // 入库单列表 upsert
                GoodcangGrnListEntity le = grnListMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GoodcangGrnListEntity>()
                                .eq(GoodcangGrnListEntity::getReceivingCode, code));
                boolean isNewList = (le == null);
                if (isNewList) {
                    le = new GoodcangGrnListEntity();
                    le.setId(uuid32());
                    le.setReceivingCode(code);
                }
                le.setWarehouseCode(str(item.get("warehouse_code")));
                le.setTransitWarehouseCode(str(item.get("transit_warehouse_code")));
                le.setReferenceNo(str(item.get("reference_no")));
                le.setReceivingStatus(intVal(item.get("receiving_status")));
                le.setTransitType(intVal(item.get("transit_type")));
                le.setCreateAt(parseDateTime(str(item.get("create_at"))));
                le.setUpdateAt(parseDateTime(str(item.get("update_at"))));
                if (isNewList) grnListMapper.insert(le);
                else grnListMapper.updateById(le);
                totalList++;

                // 入库单详情
                try {
                    Map<String, Object> detailResp = client.getGrnDetail(code);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> detailData = (Map<String, Object>) detailResp.get("data");
                    if (detailData != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> transferList = (List<Map<String, Object>>) detailData.get("transfer_detail");
                        if (transferList != null) {
                            // 删除旧明细
                            grnDetailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GoodcangGrnDetailEntity>()
                                    .eq(GoodcangGrnDetailEntity::getReceivingCode, code));
                            for (Map<String, Object> td : transferList) {
                                GoodcangGrnDetailEntity de = new GoodcangGrnDetailEntity();
                                de.setId(uuid32());
                                de.setReceivingCode(code);
                                de.setProductSku(str(td.get("product_sku")));
                                de.setBoxNo(str(td.get("box_no")));
                                de.setTransitPreCount(intVal(td.get("transit_pre_count")));
                                de.setTransitReceivingCount(intVal(td.get("transit_receiving_count")));
                                de.setReferenceBoxNo(str(td.get("reference_box_no")));
                                grnDetailMapper.insert(de);
                                totalDetail++;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 单条详情失败不影响整体
                }
            }

            page++;
            Object total = resp.get("count");
            if (total != null && Integer.parseInt(String.valueOf(total)) <= page * 200) break;
        }

        Map<String, Object> detailInfo = new HashMap<>();
        detailInfo.put("detail_count", totalDetail);
        return new SaleStatSyncResponse(totalList, totalList, Collections.singletonList(detailInfo));
    }

    private String str(Object v) { return v != null ? String.valueOf(v) : ""; }
    private int intVal(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { return null; }
    }
    /** 同步仓库信息，清空后全量重写并模糊匹配 wid */
    @Transactional
    public SaleStatSyncResponse syncWarehouses() throws Exception {
        Map<String, Object> resp = client.getWarehouses();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        if (data == null) return new SaleStatSyncResponse(0, 0, Collections.emptyList());

        // 先清空
        warehouseMapper.delete(null);

        int count = 0;
        for (Map<String, Object> wh : data) {
            String warehouseCode = str(wh.get("warehouse_code"));
            String warehouseName = str(wh.get("warehouse_name"));
            String countryCode = str(wh.get("country_code"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> wpList = (List<Map<String, Object>>) wh.get("wp_list");
            if (wpList == null) continue;

            for (Map<String, Object> wp : wpList) {
                GoodcangWarehouseEntity e = new GoodcangWarehouseEntity();
                e.setId(uuid32());
                e.setWarehouseCode(warehouseCode);
                e.setWarehouseName(warehouseName);
                e.setCountryCode(countryCode);
                e.setWpCode(str(wp.get("code")));
                e.setWpName(str(wp.get("name")));
                Map<String, Object> addr = (Map<String, Object>) wp.get("address");
                if (addr != null) {
                    e.setState(str(addr.get("state")));
                    e.setCity(str(addr.get("city")));
                    e.setPostcode(str(addr.get("postcode")));
                    e.setContacter(str(addr.get("contacter")));
                    e.setPhone(str(addr.get("phone")));
                    e.setStreetAddress1(str(addr.get("street_address1")));
                    e.setStreetAddress2(str(addr.get("street_address2")));
                    e.setStreetNumber(str(addr.get("street_number")));
                }
                warehouseMapper.insert(e);
                count++;
            }
        }

        // 一条 SQL 模糊匹配填 wid
        int matched = warehouseMapper.fillWidByFuzzyMatch();

        Map<String, Object> info = new HashMap<>();
        info.put("inserted", count);
        info.put("wid_matched", matched);
        return new SaleStatSyncResponse(count, count, Collections.singletonList(info));
    }

    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
