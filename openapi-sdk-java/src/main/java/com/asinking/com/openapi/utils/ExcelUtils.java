package com.asinking.com.openapi.utils;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Excel 单元格读取工具类，统一处理各种单元格类型的值读取。
 */
public final class ExcelUtils {

    private ExcelUtils() {}

    /** 读取单元格字符串值（null 返回空串） */
    public static String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                String v = cell.getStringCellValue();
                return v != null ? v.trim() : "";
            case NUMERIC: {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d))
                    return String.valueOf((long) d);
                return String.valueOf(d);
            }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getStringCellValue().trim(); }
            default:
                return "";
        }
    }

    /** 读取单元格字符串值，null/空串返回 null */
    public static String getCellStringOrNull(Cell cell) {
        String s = getCellString(cell);
        return s.isEmpty() ? null : s;
    }

    /** 读取单元格整数值，非数字返回 null */
    public static Integer getCellInt(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC)
                return (int) cell.getNumericCellValue();
            String s = getCellString(cell);
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取单元格整数值，非数字返回 0 */
    public static int getCellIntOrDefault(Cell cell) {
        Integer v = getCellInt(cell);
        return v != null ? v : 0;
    }

    /** 读取单元格 BigDecimal 值 */
    public static BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC)
                return BigDecimal.valueOf(cell.getNumericCellValue());
            String s = getCellString(cell).replace(",", "").replace("%", "");
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取单元格 BigDecimal 值，null 返回 ZERO */
    public static BigDecimal getCellBigDecimalOrDefault(Cell cell) {
        BigDecimal v = getCellBigDecimal(cell);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 读取百分比字符串（如 "15%" → 0.15） */
    public static BigDecimal getCellPercent(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        try {
            String s = getCellString(cell).replace(",", "").replace("%", "");
            if (s.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(s).divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 读取日期时间（yyyy-MM-dd HH:mm:ss） */
    public static LocalDateTime getCellDateTime(Cell cell) {
        String s = getCellString(cell);
        if (s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取列索引映射（表头名 → 列号） */
    public static int[] findColumnIndexes(Row headerRow, String... headers) {
        int[] indexes = new int[headers.length];
        for (int i = 0; i < headers.length; i++) indexes[i] = -1;
        if (headerRow == null) return indexes;
        for (Cell cell : headerRow) {
            String v = getCellString(cell);
            for (int i = 0; i < headers.length; i++) {
                if (v.equals(headers[i])) indexes[i] = cell.getColumnIndex();
            }
        }
        return indexes;
    }

    /** 创建加粗表头样式 */
    public static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** 写入表头行 */
    public static void writeHeader(Sheet sheet, CellStyle style, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            if (style != null) cell.setCellStyle(style);
        }
    }

    /** 生成 32 位无横线 UUID */
    public static String uuid32() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
