package oci.connection.demo.report;

import oci.connection.demo.dto.ComponentCost;
import oci.connection.demo.dto.ResourceNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExcelReportWriter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final short FONT_SIZE = 11;

    public static String write(List<ResourceNode> nodes, LocalDate from, LocalDate to,
                               String nameFilter) throws IOException {

        String fileName = "reporte_costos_" + from.format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".xlsx";

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Reporte de Costos");

            // ── estilos ──────────────────────────────────────────────────────
            CellStyle titleStyle   = titleStyle(wb);
            CellStyle headerStyle  = headerStyle(wb);
            CellStyle resourceStyle = resourceStyle(wb);
            CellStyle childStyle   = childStyle(wb);
            CellStyle componentStyle = componentStyle(wb);
            CellStyle totalStyle   = totalStyle(wb);
            CellStyle costStyle    = costStyle(wb);
            CellStyle totalCostStyle = totalCostStyle(wb);
            CellStyle childCostStyle = childCostStyle(wb);
            CellStyle childTotalCostStyle = childTotalCostStyle(wb);

            int row = 0;

            // ── título ───────────────────────────────────────────────────────
            row = writeTitle(sheet, row, from, to, nameFilter, titleStyle);

            // ── cabecera de columnas ─────────────────────────────────────────
            row = writeColumnHeader(sheet, row, headerStyle);

            // ── datos ────────────────────────────────────────────────────────
            for (ResourceNode node : nodes) {
                row = writeNode(sheet, row, node, 0,
                        resourceStyle, childStyle, componentStyle,
                        totalStyle, costStyle, totalCostStyle,
                        childCostStyle, childTotalCostStyle);
            }

            // ── anchos de columna ────────────────────────────────────────────
            sheet.setColumnWidth(0, 38 * 256);  // Recurso
            sheet.setColumnWidth(1, 22 * 256);  // Tipo
            sheet.setColumnWidth(2, 28 * 256);  // Componente
            sheet.setColumnWidth(3, 14 * 256);  // Cantidad
            sheet.setColumnWidth(4, 14 * 256);  // Costo

            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                wb.write(fos);
            }
        }

        return fileName;
    }

    // ── escritura de filas ────────────────────────────────────────────────────

    private static int writeTitle(Sheet sheet, int row, LocalDate from, LocalDate to,
                                  String nameFilter, CellStyle style) {
        Row r = sheet.createRow(row++);
        r.setHeightInPoints(18);
        String title = nameFilter != null
                ? "Reporte de Costos OCI – filtro: \"" + nameFilter + "\""
                : "Reporte de Costos OCI – todos los recursos";
        createCell(r, 0, title, style);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 4));

        Row r2 = sheet.createRow(row++);
        String period = "Periodo: " + from.format(DATE_FMT) + " → " + to.format(DATE_FMT) + " (mes a la fecha)";
        createCell(r2, 0, period, style);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 4));

        sheet.createRow(row++); // blank
        return row;
    }

    private static int writeColumnHeader(Sheet sheet, int row, CellStyle style) {
        Row r = sheet.createRow(row++);
        r.setHeightInPoints(15);
        createCell(r, 0, "Recurso",    style);
        createCell(r, 1, "Tipo",       style);
        createCell(r, 2, "Componente", style);
        createCell(r, 3, "Cantidad",   style);
        createCell(r, 4, "Costo (USD)", style);
        return row;
    }

    private static int writeNode(Sheet sheet, int rowIdx, ResourceNode node, int depth,
                                 CellStyle resourceStyle, CellStyle childStyle,
                                 CellStyle componentStyle, CellStyle totalStyle,
                                 CellStyle costStyle, CellStyle totalCostStyle,
                                 CellStyle childCostStyle, CellStyle childTotalCostStyle) {

        boolean isChild = depth > 0;
        String indent = "  ".repeat(depth);
        String name = displayName(node);

        CellStyle nameStyle  = isChild ? childStyle   : resourceStyle;
        CellStyle tStyle     = isChild ? childTotalCostStyle : totalCostStyle;
        CellStyle cCostStyle = isChild ? childCostStyle : costStyle;

        // ── fila del recurso ─────────────────────────────────────────────────
        Row resRow = sheet.createRow(rowIdx++);
        resRow.setHeightInPoints(14);
        createCell(resRow, 0, indent + name, nameStyle);
        createCell(resRow, 1, node.type() != null ? node.type() : "", nameStyle);
        createCell(resRow, 2, "", nameStyle);
        createCell(resRow, 3, "", nameStyle);
        createCell(resRow, 4, "", nameStyle);

        // ── componentes propios ───────────────────────────────────────────────
        for (ComponentCost c : node.components()) {
            if (c.cost() == null || c.cost().signum() == 0) continue;
            Row cr = sheet.createRow(rowIdx++);
            cr.setHeightInPoints(13);
            createCell(cr, 0, "", componentStyle);
            createCell(cr, 1, "", componentStyle);
            createCell(cr, 2, c.label(), componentStyle);
            createCell(cr, 3, formatQty(c.quantity(), c.unit()), componentStyle);
            createNumericCell(cr, 4, c.cost(), cCostStyle);
        }

        // ── hijos ─────────────────────────────────────────────────────────────
        for (ResourceNode child : node.children()) {
            rowIdx = writeNode(sheet, rowIdx, child, depth + 1,
                    resourceStyle, childStyle, componentStyle,
                    totalStyle, costStyle, totalCostStyle,
                    childCostStyle, childTotalCostStyle);
        }

        // ── fila total del recurso ────────────────────────────────────────────
        Row totRow = sheet.createRow(rowIdx++);
        totRow.setHeightInPoints(14);
        createCell(totRow, 0, indent + "Total", totalStyle);
        createCell(totRow, 1, node.currency() != null ? node.currency() : "", totalStyle);
        createCell(totRow, 2, "", totalStyle);
        createCell(totRow, 3, "", totalStyle);
        createNumericCell(totRow, 4,
                node.totalCost().setScale(2, RoundingMode.HALF_UP), tStyle);

        // blank entre recursos de nivel superior
        if (depth == 0) sheet.createRow(rowIdx++);

        return rowIdx;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String displayName(ResourceNode node) {
        if (node.name() != null && !node.name().isBlank()) return node.name();
        return shortOcid(node.resourceId());
    }

    private static String shortOcid(String ocid) {
        if (ocid == null || ocid.isBlank()) return "(sin id)";
        int first = ocid.indexOf('.');
        int second = first >= 0 ? ocid.indexOf('.', first + 1) : -1;
        String type = second > 0 ? ocid.substring(0, second) : ocid;
        String tail = ocid.length() > 12 ? ocid.substring(ocid.length() - 12) : ocid;
        return type + "..." + tail;
    }

    private static String formatQty(Double quantity, String unit) {
        if (quantity == null) return "-";
        long lv = Math.round(quantity);
        String num = (quantity == lv) ? String.valueOf(lv) : String.format("%.1f", quantity);
        return unit != null ? num + " " + unit : num;
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) cell.setCellStyle(style);
    }

    private static void createNumericCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0.0);
        if (style != null) cell.setCellStyle(style);
    }

    // ── fábricas de estilos ───────────────────────────────────────────────────

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font fWhite = wb.createFont();
        fWhite.setBold(true);
        fWhite.setFontHeightInPoints((short) 13);
        fWhite.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(fWhite);
        return s;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static CellStyle resourceStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static CellStyle childStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static CellStyle componentStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        setBorder(s, BorderStyle.HAIR);
        return s;
    }

    private static CellStyle totalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static CellStyle costStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        setBorder(s, BorderStyle.HAIR);
        return s;
    }

    private static CellStyle totalCostStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static CellStyle childCostStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        s.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.HAIR);
        return s;
    }

    private static CellStyle childTotalCostStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints(FONT_SIZE);
        s.setFont(f);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s, BorderStyle.THIN);
        return s;
    }

    private static void setBorder(CellStyle s, BorderStyle bs) {
        s.setBorderTop(bs);
        s.setBorderBottom(bs);
        s.setBorderLeft(bs);
        s.setBorderRight(bs);
    }
}
