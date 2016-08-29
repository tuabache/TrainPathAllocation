/*
 * Copyright 2016 Christian Eichenberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.oakmountain.tpa.web;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static ch.oakmountain.tpa.web.TpaPersistorUtils.copyFromResourceToDir;

/**
 *
 */
public class TablePersistor {

    private static final Logger LOGGER = LogManager.getLogger();

    private final File file;

    public TablePersistor(String name, String outputDir, String title, List<String> header) throws IOException {
        this.file = Paths.get(outputDir + File.separator + name + ".html").toFile();
        createTable(name, outputDir, title, header);
    }

    // Source: https://www.datatables.net/manual/styling/bootstrap-simple.html
    public void createTable(String name, String outputDir, String title, List<String> header) throws IOException {
        LOGGER.info("Creating table " + name + "....");


        writeTableHeader(title, header);


        copyFromResourceToDir("datatables.min.css", outputDir, "DataTables");
        copyFromResourceToDir("datatables.min.js", outputDir, "DataTables");

        copyFromResourceToDir("sort_asc.png", outputDir, "DataTables/DataTables-1.10.10/images");
        copyFromResourceToDir("sort_asc_disabled.png", outputDir, "DataTables/DataTables-1.10.10/images");
        copyFromResourceToDir("sort_both.png", outputDir, "DataTables/DataTables-1.10.10/images");
        copyFromResourceToDir("sort_desc.png", outputDir, "DataTables/DataTables-1.10.10/images");
        copyFromResourceToDir("sort_desc_disabled.png", outputDir, "DataTables/DataTables-1.10.10/images");

        LOGGER.info("... created table " + name + ".");
    }

    private void writeTableHeader(String title, List<String> header) throws IOException {

        String head = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "\n" +
                "\t<title>" + title + "</title>\n" +
                "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"DataTables/dataTables.min.css\">\n" +
                "\t<script type=\"text/javascript\" language=\"javascript\" src=\"DataTables/dataTables.min.js\">\n" +
                "\t</script>\n" +
                "\t<script type=\"text/javascript\" class=\"init\">\n" +
                "\t\n" +
                "\n" +
                "$(document).ready(function() {\n" +
                "    // Setup - add a text input to each footer cell\n" +
                "    $('#example tfoot th').each( function () {\n" +
                "        var title = $(this).text();\n" +
                "        $(this).html( '<input type=\"text\" placeholder=\"Search '+title+'\" />' );\n" +
                "    } );\n" +
                " \n" +
                "    // DataTable\n" +
                "    var table = $('#example').DataTable();\n" +
                " \n" +
                "    // Apply the search\n" +
                "    table.columns().every( function () {\n" +
                "        var that = this;\n" +
                " \n" +
                "        $( 'input', this.footer() ).on( 'keyup change', function () {\n" +
                "            if ( that.search() !== this.value ) {\n" +
                "                that\n" +
                "                    .search( this.value )\n" +
                "                    .draw();\n" +
                "            }\n" +
                "        } );\n" +
                "    } );\n" +
                "} );" +
                "\n" +
                "\n" +
                "\t</script>\n" +
                "</head>\n" +
                "<body>\n" +
                "\t<h1 class=\"page_title\">" + title + "</h1>\n" +
                "\t<table id=\"example\" class=\"display\" cellspacing=\"0\" width=\"100%\">\n";
        String theadStart = "<thead><tr>";
        String theadEnd = "</tr></thead>";
        String tfootStart = "<tfoot><tr>";
        String tfootEnd = "</tr></tfoot>";
        String bodyStart = "<tbody>";

        FileUtils.writeStringToFile(file, head, false);
        FileUtils.writeStringToFile(file, theadStart, true);
        for (String s : header) {
            FileUtils.writeStringToFile(file, "<th>" + s + "</th>", true);

        }
        FileUtils.writeStringToFile(file, theadEnd, true);
        FileUtils.writeStringToFile(file, tfootStart, true);
        for (String s : header) {
            FileUtils.writeStringToFile(file, "<th>" + s + "</th>", true);

        }
        FileUtils.writeStringToFile(file, tfootEnd, true);
        FileUtils.writeStringToFile(file, bodyStart, true);
    }

    public void finishTable() throws IOException {
        writeTableFooter();
    }

    private void writeTableFooter() throws IOException {
        String footer = "</tbody>\n" +
                "\t</table>\n" +
                "</body>\n" +
                "</html>";
        FileUtils.writeStringToFile(file, footer, true);
    }

    public void writeRow(List<String> tableData) throws IOException {
        FileUtils.writeStringToFile(file, "<tr>", true);
        for (String s : tableData) {
            FileUtils.writeStringToFile(file, "<td>" + s + "</td>", true);
        }
        FileUtils.writeStringToFile(file, "</tr>", true);
    }
}
