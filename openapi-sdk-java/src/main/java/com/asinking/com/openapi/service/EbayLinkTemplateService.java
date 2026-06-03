package com.asinking.com.openapi.service;

import com.asinking.com.openapi.entity.EbayLinkTemplateEntity;
import com.asinking.com.openapi.mapper.mp.EbayLinkTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * eBay 链接模板服务：模板存数据库，直接读表（只有3条，无需缓存）。
 * 修改即时生效，无需重启。
 */
@Service
public class EbayLinkTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(EbayLinkTemplateService.class);
    private final EbayLinkTemplateMapper mapper;

    public EbayLinkTemplateService(EbayLinkTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        if (mapper.selectList(null).isEmpty()) {
            insertDefault("美国",
                    "https://www.ebay.com/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&_fcid=1&_sop=15",
                    "https://www.ebay.com/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&LH_Complete=1&rt=nc&LH_Sold=1&_fcid=1");
            insertDefault("英国",
                    "https://www.ebay.co.uk/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&_sop=15",
                    "https://www.ebay.co.uk/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&LH_Complete=1&rt=nc&LH_Sold=1");
            insertDefault("德国",
                    "https://www.ebay.de/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&_trksid=m570.l1313&_odkw=289902F710&_osacat=0&_sop=15",
                    "https://www.ebay.de/sch/i.html?_nkw={oe}&_sacat=0&_from=R40&LH_Complete=1&LH_Sold=1&_sop=10");
            LOG.info("eBay 链接模板初始化完成（首次写入默认值）");
        }
    }

    private void insertDefault(String site, String presale, String sold) {
        EbayLinkTemplateEntity e = new EbayLinkTemplateEntity();
        e.setSite(site);
        e.setPresaleUrl(presale);
        e.setSoldUrl(sold);
        mapper.insert(e);
    }

    /** 构建售前链接 */
    public String buildPresaleUrl(String site, String oe) {
        EbayLinkTemplateEntity t = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EbayLinkTemplateEntity>()
                        .eq(EbayLinkTemplateEntity::getSite, site));
        LOG.debug("buildPresaleUrl site={}, found={}", site, t != null);
        return t != null && t.getPresaleUrl() != null
                ? t.getPresaleUrl().replace("{oe}", oe != null ? oe : "") : null;
    }

    /** 构建售后链接 */
    public String buildSoldUrl(String site, String oe) {
        EbayLinkTemplateEntity t = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EbayLinkTemplateEntity>()
                        .eq(EbayLinkTemplateEntity::getSite, site));
        return t != null && t.getSoldUrl() != null
                ? t.getSoldUrl().replace("{oe}", oe != null ? oe : "") : null;
    }

    /** 保存模板（新增或更新） */
    public void save(String site, String presaleUrl, String soldUrl) {
        EbayLinkTemplateEntity e = new EbayLinkTemplateEntity();
        e.setSite(site);
        e.setPresaleUrl(presaleUrl);
        e.setSoldUrl(soldUrl);
        if (mapper.selectById(site) != null) {
            mapper.updateById(e);
        } else {
            mapper.insert(e);
        }
    }

    /** 删除模板 */
    public void delete(String site) {
        mapper.deleteById(site);
    }

    public List<EbayLinkTemplateEntity> listAll() {
        return mapper.selectList(null);
    }
}
