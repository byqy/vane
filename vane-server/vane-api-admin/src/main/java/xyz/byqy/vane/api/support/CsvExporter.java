package xyz.byqy.vane.api.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

/** CSV 导出：UTF-8 BOM 便于 Excel 正确识别中文，字段按 RFC 4180 转义。 */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static void write(HttpServletResponse response, String filename, List<String> headers,
                             List<List<String>> rows) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(join(headers)).append("\r\n");
        for (List<String> row : rows) {
            csv.append(join(row)).append("\r\n");
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private static String join(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells.get(i)));
        }
        return line.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        if (!needQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
