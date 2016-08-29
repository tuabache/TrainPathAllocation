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
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class TpaWebPersistor implements IGraphPersistor, IMatrixPersistor {
    private static final Logger LOGGER = LogManager.getLogger();

    private final File file;

    public TpaWebPersistor(File file) {
        this.file = file;
    }

    public static void createGraph(String name, String outputDir, GraphCSV csv, String htmlData) throws IOException {
        String content = IOUtils.toString(TpaWebPersistor.class.getResourceAsStream("/graph-curved.html"));
        content = content.replaceAll(Pattern.quote("$CSVNAME$"), Matcher.quoteReplacement(name)).replaceAll(Pattern.quote("$HTML$"), Matcher.quoteReplacement(htmlData));

        FileUtils.writeStringToFile(Paths.get(outputDir + File.separator + name + ".html").toFile(), content);
        FileUtils.writeStringToFile(new File
                (outputDir + File.separator + name + ".csv"), csv.toString());
        TpaPersistorUtils.copyFromResourceToDir("d3.v3.js", outputDir);
    }



    public static void createGraph(String name, String outputDir, IGraph graph, String htmlData) throws IOException {
        LOGGER.info("Creating graph " + name + "....");

        String content = IOUtils.toString(TpaWebPersistor.class.getResourceAsStream("/graph-curved.html"));
        content = content.replaceAll(Pattern.quote("$CSVNAME$"), Matcher.quoteReplacement(name)).replaceAll(Pattern.quote("$HTML$"), Matcher.quoteReplacement(htmlData));

        FileUtils.writeStringToFile(Paths.get(outputDir + File.separator + name + ".html").toFile(), content);
        File file = new File
                (outputDir + File.separator + name + ".csv");
        writeGraph(file, graph);
        TpaPersistorUtils.copyFromResourceToDir("d3.v3.js", outputDir);

        LOGGER.info("... created graph " + name + ".");
    }

    public static void createMatrix(String name, String outputDir, IMatrix matrix, String htmlData) throws Exception {
        LOGGER.info("Creating matrix " + name + "....");

        String content = IOUtils.toString(TpaWebPersistor.class.getResourceAsStream("/matrix.html"));
        content = content.replaceAll(Pattern.quote("$JSONNAME$"), Matcher.quoteReplacement(name)).replaceAll(Pattern.quote("$HTML$"), Matcher.quoteReplacement(htmlData));

        FileUtils.writeStringToFile(Paths.get(outputDir + File.separator + name + ".html").toFile(), content);
        File file = new File
                (outputDir + File.separator + name + ".json");
        writeMatrix(file, matrix);
        TpaPersistorUtils.copyFromResourceToDir("d3.v2.min.js", outputDir);
        TpaPersistorUtils.copyFromResourceToDir("miserables.css", outputDir);

        LOGGER.info("... created matrix " + name + ".");
    }

    private static void writeGraph(File file, IGraph graph) throws IOException {

        String header = "source,target,category,linkDescription,sourceGroup,targetGroup,sourceGroupDescription,targetGroupDescription" + System.lineSeparator();
        FileUtils.writeStringToFile(file, header);
        TpaWebPersistor graphweb = new TpaWebPersistor(file);
        graph.writeLines(graphweb);
    }

    private static void writeMatrix(File file, IMatrix matrix) throws Exception {
        String intro = "{\"nodes\":[{}";
        String middle = "],\"links\":[{}";
        String end = "]}";
        FileUtils.writeStringToFile(file, intro, false);
        for (Object node : matrix.getNodes()) {
            String s = ",{\"name\":\"" + node.toString() + "\",\"group\":1}";
            FileUtils.writeStringToFile(file, s, true);
        }
        FileUtils.writeStringToFile(file, middle, true);
        TpaWebPersistor persistor = new TpaWebPersistor(file);
        matrix.getEdges(persistor);
        FileUtils.writeStringToFile(file, end, true);

    }


    @Override
    public void addGraphLink(String source, String target, String category, String linkDescription, String sourceGroup, String targetGroup, String sourceGroupDescription, String targetGroupDescription) throws IOException {
        String line = "\"" + source + "\",\"" + target + "\",\"" + category + "\",\"" + linkDescription + "\",\"" + sourceGroup + "\",\"" + targetGroup + "\",\"" + sourceGroupDescription + "\",\"" + targetGroupDescription + "\"" + System.lineSeparator();
        FileUtils.writeStringToFile(file, line, true);
    }

    @Override
    public void appendEdge(int source, int target, int value) throws IOException {
        String line = ",{\"source\":" + source + ",\"target\":" + target + ",\"value\":" + value + "}";
        FileUtils.writeStringToFile(file, line, true);
    }
}
