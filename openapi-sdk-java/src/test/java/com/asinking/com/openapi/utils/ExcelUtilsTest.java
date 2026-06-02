package com.asinking.com.openapi.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelUtils 单元测试。
 */
class ExcelUtilsTest {

    private Cell createStringCell(Workbook wb, String value) {
        Sheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        return cell;
    }

    private Cell createNumericCell(Workbook wb, double value) {
        Sheet sheet = wb.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        return cell;
    }

    @Test
    void getCellString_shouldReturnTrimmedString() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, " hello ");
            assertEquals("hello", ExcelUtils.getCellString(cell));
        }
    }

    @Test
    void getCellString_shouldReturnEmptyForNullCell() {
        assertEquals("", ExcelUtils.getCellString(null));
    }

    @Test
    void getCellString_shouldConvertNumericToString() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createNumericCell(wb, 42.0);
            assertEquals("42", ExcelUtils.getCellString(cell));
        }
    }

    @Test
    void getCellStringOrNull_shouldReturnNullForEmpty() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, "");
            assertNull(ExcelUtils.getCellStringOrNull(cell));
        }
    }

    @Test
    void getCellInt_shouldReturnIntegerValue() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createNumericCell(wb, 100.0);
            assertEquals(100, ExcelUtils.getCellInt(cell));
        }
    }

    @Test
    void getCellInt_shouldReturnNullForNullCell() {
        assertNull(ExcelUtils.getCellInt(null));
    }

    @Test
    void getCellIntOrDefault_shouldReturnZeroForNull() {
        assertEquals(0, ExcelUtils.getCellIntOrDefault(null));
    }

    @Test
    void getCellBigDecimal_shouldParseDecimalString() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, "12.5");
            assertEquals(new BigDecimal("12.5"), ExcelUtils.getCellBigDecimal(cell));
        }
    }

    @Test
    void getCellBigDecimal_shouldHandleCommas() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, "1,234.56");
            assertEquals(new BigDecimal("1234.56"), ExcelUtils.getCellBigDecimal(cell));
        }
    }

    @Test
    void getCellPercent_shouldConvertPercentString() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, "15%");
            assertEquals(new BigDecimal("0.150000"), ExcelUtils.getCellPercent(cell));
        }
    }

    @Test
    void getCellPercent_shouldReturnZeroForNull() {
        assertEquals(BigDecimal.ZERO, ExcelUtils.getCellPercent(null));
    }

    @Test
    void getCellDateTime_shouldParseDateTime() {
        try (Workbook wb = new XSSFWorkbook()) {
            Cell cell = createStringCell(wb, "2025-06-01 12:30:00");
            LocalDateTime expected = LocalDateTime.of(2025, 6, 1, 12, 30, 0);
            assertEquals(expected, ExcelUtils.getCellDateTime(cell));
        }
    }

    @Test
    void findColumnIndexes_shouldFindCorrectColumns() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("站点");
            header.createCell(1).setCellValue("SKU");
            header.createCell(2).setCellValue("品牌");

            int[] idx = ExcelUtils.findColumnIndexes(header, "站点", "SKU", "品牌");
            assertArrayEquals(new int[]{0, 1, 2}, idx);
        }
    }

    @Test
    void findColumnIndexes_shouldReturnMinusOneForMissing() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("A");

            int[] idx = ExcelUtils.findColumnIndexes(header, "A", "B");
            assertEquals(0, idx[0]);
            assertEquals(-1, idx[1]);
        }
    }

    @Test
    void uuid32_shouldReturn32Chars() {
        String uuid = ExcelUtils.uuid32();
        assertEquals(32, uuid.length());
        assertFalse(uuid.contains("-"));
    }

    @Test
    void createHeaderStyle_shouldSetBold() {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle style = ExcelUtils.createHeaderStyle(wb);
            assertNotNull(style);
            Font font = wb.getFontAt(style.getFontIndex());
            assertTrue(font.getBold());
        }
    }
}
