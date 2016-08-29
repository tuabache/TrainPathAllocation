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

package ch.oakmountain.tpa.parser;

import ch.oakmountain.tpa.solver.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.joda.time.LocalTime;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

import static org.joda.time.DateTimeConstants.*;

/**
 *
 */
public class TpaParser {
    public static final Marker corrupt_input = MarkerManager.getMarker("CORRUPT_INPUT");
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String lastNonEmptyColPrefix = "#";
    private static final int lastNonEmptyColNb = Integer.MAX_VALUE;
    private final Properties applicationProps;
    private final Workbook wb;
    private final Map<String, String> mapping;
    private final CellStyle timestyle;
    private final HSSFDataFormatter formatter = new HSSFDataFormatter();
    private Map<Pair<String, Integer>, Integer> slotIdMap = new HashMap<>();

    public TpaParser(Properties applicationProps, String fileName) throws IOException {
        this.applicationProps = applicationProps;
        Set<String> stringPropertyNames = applicationProps.stringPropertyNames();
        for (tpaProps tpaProp : tpaProps.values()) {
            if (!stringPropertyNames.contains(tpaProp.name())) {
                throw new IllegalArgumentException("Property \"" + tpaProp.name() + "\" has not been set in the the tpa configuration properties");
            }
        }

        FileInputStream fin = new FileInputStream(fileName);
        wb = new HSSFWorkbook(fin);
        mapping = readNodeAbbrMapping(wb);
        timestyle = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        timestyle.setDataFormat(df.getFormat("hh:mm:ss"));

    }

    public void writeFile(String fileName) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        wb.write(out);
        out.close();
    }

    private String getPropertyValue(tpaProps prop) {
        return applicationProps.getProperty(prop.name());
    }

    private String getUniqueSystemNode(String name) {
        if (mapping == null || !mapping.containsKey(name)) {
            return name;
        } else {
            return mapping.get(name);
        }
    }

    private void emptyWorksheetColumns(String wsName, int rowsFrom, List<Integer> colsToDelete) {
        String trainpathsNoOperatingDayMarker = getPropertyValue(tpaProps.TRAINPATHS_NO_OPERATING_DAY_MARKER);

        Sheet sheet = wb.getSheet(wsName);
        for (int i = rowsFrom; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            for (Integer j : colsToDelete) {
                Cell cell = row.getCell(j);
                if (cell != null && !formatter.formatCellValue(cell).equals(trainpathsNoOperatingDayMarker)) {
                    cell.setCellValue((String) null);
                }
            }
        }
    }

    /**
     * @param wsName
     * @param rowsFrom
     * @param colsToDelete
     */
    private void resetRequestAllocations(String wsName, int rowsFrom, List<Integer> colsToDelete) {
        String requestsAllocatedDayMarker = getPropertyValue(tpaProps.REQUESTS_ALLOCATED_DAY_MARKER);
        String requestsRequestedDayMarker = getPropertyValue(tpaProps.REQUESTS_REQUESTED_DAY_MARKER);

        Sheet sheet = wb.getSheet(wsName);
        for (int i = rowsFrom; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            for (Integer j : colsToDelete) {
                Cell cell = row.getCell(j);
                if (cell != null && formatter.formatCellValue(cell).equals(requestsAllocatedDayMarker)) {
                    cell.setCellValue(requestsRequestedDayMarker);
                }
            }
        }
    }


    /**
     * Put the train path ids in the format <train_path_slot_id><hour_of_day_>-<three_digit_sequence_number_within_hour>
     *
     * @param wsName
     * @param rowsFrom
     * @param cols
     */
    private void correctTrainPathIds(String wsName, int rowsFrom, Map<ColumnIdentifier, Integer> cols) {

        Sheet sheet = wb.getSheet(wsName);
        for (int i = rowsFrom; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Map<ColumnIdentifier, String> line = getWorksheetPointerStringMap(cols, row);
            String uncorrectedSlotName = line.get(trainPathLayout.ID);
            if (StringUtils.isBlank(uncorrectedSlotName)) {
                continue;
            }
            try {
                LocalTime startTime = LocalTime.parse(line.get(trainPathLayout.DEPTIME));

                String correctedSlotName = getNextSlotId(wsName, startTime.getHourOfDay());
                if (!correctedSlotName.equals(uncorrectedSlotName)) {
                    LOGGER.warn("Correcting slot name " + uncorrectedSlotName + " => " + correctedSlotName);
                    row.getCell(cols.get(trainPathLayout.ID)).setCellValue(correctedSlotName);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn(corrupt_input, "Illegal start time \"" + line.get(trainPathLayout.DEPTIME) + "\" for slot " + uncorrectedSlotName + " in sheet " + wsName + " found; skipping this slot.", e);
                continue;
            }
        }
    }

    private List<Map<ColumnIdentifier, String>> readWorksheet(String wsName, int headerRowsNb, Map<ColumnIdentifier, Integer> cols) {

        List<Map<ColumnIdentifier, String>> output = new LinkedList<Map<ColumnIdentifier, String>>();

        Sheet sheet = wb.getSheet(wsName);

        for (int i = headerRowsNb; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Map<ColumnIdentifier, String> outputItem = getWorksheetPointerStringMap(cols, row);
            output.add(outputItem);
        }
        return output;
    }

    private Map<ColumnIdentifier, String> getWorksheetPointerStringMap(Map<ColumnIdentifier, Integer> cols, Row row) {
        Map<ColumnIdentifier, String> outputItem = new HashMap<ColumnIdentifier, String>(cols.size());
        for (ColumnIdentifier col : cols.keySet()) {
            int colIndex = cols.get(col);
            Cell cell;
            if (colIndex == lastNonEmptyColNb) {
                colIndex = row.getLastCellNum();
                cell = row.getCell(colIndex);
                for (; colIndex > 0; colIndex--) {
                    cell = row.getCell(colIndex);
                    if (StringUtils.isNotBlank(formatter.formatCellValue(cell))) {
                        String test = mapping.get(formatter.formatCellValue(cell));
                        if (test == null) {
                            LOGGER.error("Could not find mapping for " + formatter.formatCellValue(cell));
                        }
                        break;
                    }
                }
            } else {
                cell = row.getCell(colIndex);
            }
            outputItem.put(col, getCellValueString(cell));
        }
        return outputItem;
    }

    private String getCellValueString(Cell cell) {
        if (cell != null && cell.getCellType() == Cell.CELL_TYPE_NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            cell.setCellStyle(timestyle);
        }
        return formatter.formatCellValue(cell);
    }

    private Hashtable<String, String> readNodeAbbrMapping(Workbook wb) {
        Sheet sheet = wb.getSheet(getPropertyValue(tpaProps.TRAINPATHSECTION_NODES_TO_SYSTEM_NODES_MAPPING_WS_NAME));
        Hashtable<String, String> hashtable = new Hashtable<String, String>();

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Cell cellAbbr = row.getCell(0);
            if (cellAbbr == null || cellAbbr.getCellType() == HSSFCell.CELL_TYPE_BLANK) {
                continue;
            }
            Cell cellUnique = row.getCell(1);
            if (cellUnique == null || cellUnique.getCellType() == HSSFCell.CELL_TYPE_BLANK) {
                continue;
            }
            hashtable.put(formatter.formatCellValue(cellAbbr), formatter.formatCellValue(cellUnique));
        }
        return hashtable;
    }

    public MacroscopicTopology readMacroscopicTopology() throws IOException {
        MacroscopicTopology macroscopicTopology = new MacroscopicTopology();


        String colLayoutString = getPropertyValue(tpaProps.TRAINPATHSECTIONS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(trainPathSectionLayout.values().length);
        for (trainPathSectionLayout l : trainPathSectionLayout.values()) {
            cols.add(l);
        }

        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);


        String wsName = getPropertyValue(tpaProps.TRAINPATHSECTIONS_WS_NAME);
        int headerRowsNb = Integer.parseInt(getPropertyValue(tpaProps.TRAINPATHSECTIONS_WS_HEADER_ROWS));
        List<Map<ColumnIdentifier, String>> lines = readWorksheet(wsName, headerRowsNb, colLayoutMapping);

        for (Map<ColumnIdentifier, String> line : lines) {
            String trainPathSectionName = line.get(trainPathSectionLayout.ID);
            String fromName = line.get(trainPathSectionLayout.FROM);
            String toName = line.get(trainPathSectionLayout.TO);

            if (StringUtils.isBlank(trainPathSectionName)) {
                continue;
            }
            fromName = getUniqueSystemNode(fromName);
            toName = getUniqueSystemNode(toName);
            macroscopicTopology.link(trainPathSectionName, fromName, toName);
        }


        List<SystemNode> endPoints = getTerminalSystemNodes(macroscopicTopology);
        List<List<SystemNode>> routes = macroscopicTopology.findRoutesByEndPoints(endPoints);
        if (routes.size() != endPoints.size() * (endPoints.size() - 1)) {
            LOGGER.warn("Expected to find " + (endPoints.size() * (endPoints.size() - 1)) + "; found: " + routes.size() + "; are train path sections defined in both directions?");
        }
        macroscopicTopology.addRoutes(routes);

        return macroscopicTopology;
    }

    public List<SystemNode> getTerminalSystemNodes(MacroscopicTopology macroscopicTopology) {
        List<SystemNode> endPoints = new LinkedList<>();
        List<String> terminalSystemNodeNames = Arrays.asList(getPropertyValue(tpaProps.TERMINALSYSTEMNODES).split("\\s*,\\s*"));
        for (String terminalSystemNodeName : terminalSystemNodeNames) {
            endPoints.add(macroscopicTopology.getSystemNode(terminalSystemNodeName));
        }
        return endPoints;
    }

    private Map<ColumnIdentifier, Integer> getColLayoutMapping(String colLayoutString, List<ColumnIdentifier> cols) {

        List<String> colLayoutList = Arrays.asList(colLayoutString.split(","));

        Map<ColumnIdentifier, Integer> colLayoutMapping = new HashMap<ColumnIdentifier, Integer>();

        for (ColumnIdentifier col : cols) {
            if (colLayoutList.indexOf(col.toString()) >= 0) {
                colLayoutMapping.put(col, colLayoutList.indexOf(col.toString()));
            } else if (colLayoutList.indexOf(lastNonEmptyColPrefix + col.toString()) >= 0) {
                colLayoutMapping.put(col, lastNonEmptyColNb);
            } else {
                throw new IllegalArgumentException("Could not find column " + col.name() + " in the column layout");
            }
        }
        return colLayoutMapping;
    }

    public TrainPathSlotCatalogue readTrainPathCatalogue(MacroscopicTopology macroscopicTopology, boolean clean, boolean correctTrainPathIds) {
        List<String> linkNames = macroscopicTopology.getLinkNames();
        TrainPathSlotCatalogue catalogue = new TrainPathSlotCatalogue();


        String colLayoutString = getPropertyValue(tpaProps.TRAINPATHS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(trainPathLayout.values().length);
        for (trainPathLayout l : trainPathLayout.values()) {
            cols.add(l);
        }

        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);

        for (String linkName : linkNames) {
            int headerRowsNb = Integer.parseInt(getPropertyValue(tpaProps.TRAINPATHS_WS_HEADER_ROWS));

            if (clean) {
                List<Integer> colsToDelete = new LinkedList<>();
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.MON));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.TUE));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.WED));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.THU));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.FRI));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.SAT));
                colsToDelete.add(colLayoutMapping.get(trainPathLayout.SUN));
                emptyWorksheetColumns(linkName, headerRowsNb, colsToDelete);
            }

            if (correctTrainPathIds) {
                correctTrainPathIds(linkName, headerRowsNb, colLayoutMapping);
            }

            List<Map<ColumnIdentifier, String>> lines = readWorksheet(linkName, headerRowsNb, colLayoutMapping);


            Pair<SystemNode, SystemNode> link = macroscopicTopology.getLink(linkName);
            for (Map<ColumnIdentifier, String> line : lines) {
                if (StringUtils.isBlank(line.get(trainPathLayout.ID))) {
                    continue;
                } else if (StringUtils.isBlank(line.get(trainPathLayout.DEPTIME))) {
                    LOGGER.warn(corrupt_input, "Train path slot " + line.get(trainPathLayout.ID) + " has empty start time!");
                    continue;
                } else if (StringUtils.isBlank(line.get(trainPathLayout.ARRTIME))) {
                    LOGGER.warn(corrupt_input, "Train path slot " + line.get(trainPathLayout.ID) + " has empty end time!");
                    continue;
                }

                String slotName = line.get(trainPathLayout.ID);
                LocalTime startTime = LocalTime.parse(line.get(trainPathLayout.DEPTIME));
                LocalTime endTime = LocalTime.parse(line.get(trainPathLayout.ARRTIME));
                try {
                    SystemNode from = link.first;
                    SystemNode to = link.second;

                    Periodicity periodicity = new Periodicity();
                    for (trainPathLayout weekDay : trainPathLayout.weekDays) {
                        periodicity.setWeekDay(trainPathLayout.getWeekDay(weekDay), StringUtils.isBlank(line.get(weekDay)));
                    }
                    catalogue.add(linkName, slotName, startTime, endTime, from, to, periodicity);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn(corrupt_input, "Skipping slot " + slotName, e);
                    continue;
                }
            }
        }
        return catalogue;
    }

    public void generateRequests(MacroscopicTopology macroscopicTopology, int requestsPerHour, int durationMinutes, int offset, String from, String to, Periodicity p) {
        String colLayoutString = getPropertyValue(tpaProps.REQUESTS_COL_LAYOUT);

        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(requestsLayout.values().length);
        for (requestsLayout l : requestsLayout.values()) {
            cols.add(l);
        }
        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);

        int arrtimeColIndex = colLayoutMapping.get(requestsLayout.ARRTIME);
        int fromColIndex = colLayoutMapping.get(requestsLayout.FROM);
        int toColIndex = colLayoutMapping.get(requestsLayout.TO);
        int deptimeColIndex = colLayoutMapping.get(requestsLayout.DEPTIME);
        int idColIndex = colLayoutMapping.get(requestsLayout.ID);
        String requestedMarker = getPropertyValue(tpaProps.REQUESTS_REQUESTED_DAY_MARKER);

        List<String> linkNames = macroscopicTopology.getLinkNames();
        Sheet sheet = wb.getSheet(getPropertyValue(tpaProps.REQUESTS_WS_NAME));

        // header
        Row headerRow = sheet.createRow(0);
        for (ColumnIdentifier col : colLayoutMapping.keySet()) {
            int i = colLayoutMapping.get(col);
            Cell cell = headerRow.getCell(i, Row.CREATE_NULL_AS_BLANK);
            cell.setCellValue(col.name());
        }
        // train path slots hourly
        int k = 1;
        for (int i = 0; i < 24; i++) {
            for (int j = 0; j < requestsPerHour; j++) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                int hour = i;
                int minutes = (offset + j * (60 / requestsPerHour)) % 60;
                double deptime = DateUtil.convertTime(
                        String.format("%02d", hour) + ":" + String.format("%02d", minutes));
                double arrtime = (deptime * 24 * 60 + durationMinutes) / (24 * 60);

                Cell cell = row.getCell(deptimeColIndex, Row.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(timestyle);
                cell.setCellValue(deptime);
                cell = row.getCell(arrtimeColIndex, Row.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(timestyle);
                cell.setCellValue(arrtime);

                row.getCell(fromColIndex, Row.CREATE_NULL_AS_BLANK).setCellValue(from);
                row.getCell(toColIndex, Row.CREATE_NULL_AS_BLANK).setCellValue(to);

                row.getCell(idColIndex, Row.CREATE_NULL_AS_BLANK).setCellValue(from + "_" + to + "_" + String.format("%03d", k));
                k++;

                // peridiocity
                for (Integer integer : p.getWeekDays()) {
                    int weekdayColIndex = colLayoutMapping.get(requestsLayout.getWeekDayTrainPathLayout(integer));
                    row.getCell(weekdayColIndex, Row.CREATE_NULL_AS_BLANK).setCellValue(requestedMarker);
                }
            }
        }
    }

    public void addCatalogue(MacroscopicTopology macroscopicTopology, TrainPathSlotCatalogue catalogue) {
        String colLayoutString = getPropertyValue(tpaProps.TRAINPATHS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(trainPathLayout.values().length);
        for (trainPathLayout l : trainPathLayout.values()) {
            cols.add(l);
        }
        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);
        int deptimeColIndex = colLayoutMapping.get(trainPathLayout.DEPTIME);
        int arrtimeColIndex = colLayoutMapping.get(trainPathLayout.ARRTIME);
        int idColIndex = colLayoutMapping.get(trainPathLayout.ID);


        for (PeriodicalTrainPathSlot periodicalTrainPathSlot : catalogue.getTrainPathSlots()) {
            String linkName = periodicalTrainPathSlot.getTrainPathSectionName();

            // Create sheet if it does not exist yet
            if (wb.getSheet(linkName) == null) {
                Sheet sheet = wb.createSheet(linkName);
                // header
                Row headerRow = sheet.createRow(0);
                for (ColumnIdentifier col : colLayoutMapping.keySet()) {
                    int i = colLayoutMapping.get(col);
                    Cell cell = headerRow.getCell(i, Row.CREATE_NULL_AS_BLANK);
                    cell.setCellValue(col.name());
                }
            }

            Sheet sheet = wb.getSheet(linkName);
            int rowNb;
            for (rowNb = 1; rowNb < sheet.getPhysicalNumberOfRows(); rowNb++) {
                if (sheet.getRow(rowNb) == null || StringUtils.isBlank(getCellValueString(sheet.getRow(rowNb).getCell(idColIndex, Row.CREATE_NULL_AS_BLANK)))) {
                    break;
                }
            }
            Row row = sheet.createRow(rowNb);

            TrainPathSlot slot = periodicalTrainPathSlot.getSlots().get(0);
            int depHour = slot.getStartTime().getHourOfDay();
            int depMinutes = slot.getStartTime().getMinuteOfHour();
            int arrHour = slot.getEndTime().getHourOfDay();
            int arrMinutes = slot.getEndTime().getMinuteOfHour();
            double deptime = DateUtil.convertTime(
                    String.format("%02d", depHour) + ":" + String.format("%02d", depMinutes));
            double arrtime = DateUtil.convertTime(
                    String.format("%02d", arrHour) + ":" + String.format("%02d", arrMinutes));

            Cell cell = row.getCell(deptimeColIndex, Row.CREATE_NULL_AS_BLANK);
            cell.setCellStyle(timestyle);
            cell.setCellValue(deptime);
            cell = row.getCell(arrtimeColIndex, Row.CREATE_NULL_AS_BLANK);
            cell.setCellStyle(timestyle);
            cell.setCellValue(arrtime);

            cell = row.getCell(idColIndex, Row.CREATE_NULL_AS_BLANK);
            cell.setCellValue(periodicalTrainPathSlot.getName());
        }
    }

    private String getNextSlotId(String trainPathSectionName, int startHour) {
        Pair key = new Pair(trainPathSectionName, startHour);
        if (!slotIdMap.containsKey(key)) {
            slotIdMap.put(key, 1);
        }
        String nextSlotId = trainPathSectionName + "_" + String.format("%02d", startHour) + "-" + String.format("%03d", slotIdMap.get(key));
        slotIdMap.put(key, slotIdMap.put(key, 0) + 1);
        return nextSlotId;
    }

    public List<SimpleTrainPathApplication> extractRequestsFromAllocations(MacroscopicTopology macroscopicTopology) throws ParseException {
        List<SimpleTrainPathApplication> simpleTrainPathApplications = new LinkedList<SimpleTrainPathApplication>();


        String colLayoutString = getPropertyValue(tpaProps.REQUESTS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(requestsLayout.values().length);
        for (requestsLayout l : requestsLayout.values()) {
            cols.add(l);
        }

        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);


        String wsName = getPropertyValue(tpaProps.REQUESTS_WS_NAME);
        int headerRowsNb = Integer.parseInt(getPropertyValue(tpaProps.REQUESTS_WS_HEADER_ROWS));
        List<Map<ColumnIdentifier, String>> lines = readWorksheet(wsName, headerRowsNb, colLayoutMapping);

        // Map is used to generate requests from current allocations
        Map<String, Map<ColumnIdentifier, String>> map = new HashMap<String, Map<ColumnIdentifier, String>>();
        for (Map<ColumnIdentifier, String> line : lines) {
            String requestNr = line.get(requestsLayout.ID);
            if (StringUtils.isBlank(requestNr)) {
                continue;
            } else if (StringUtils.isBlank(line.get(requestsLayout.FROM))) {
                LOGGER.warn("Request " + requestNr + " has empty start node!");
                continue;
            } else if (StringUtils.isBlank(line.get(requestsLayout.TO))) {
                LOGGER.warn("Request " + requestNr + " has empty end node!");
                continue;
            } else if (StringUtils.isBlank(line.get(requestsLayout.DEPTIME))) {
                LOGGER.warn("Request " + requestNr + " has empty departure time!");
                continue;
            } else if (StringUtils.isBlank(line.get(requestsLayout.ARRTIME))) {
                LOGGER.warn("Request " + requestNr + " has empty arrival time!");
                continue;
            }


            if (!map.containsKey(requestNr)) {
                map.put(requestNr, line);
            } else {
                Map<ColumnIdentifier, String> lineInMap = map.get(requestNr);
                for (requestsLayout weekDay : requestsLayout.weekDays) {
                    if (StringUtils.isNotBlank(lineInMap.get(weekDay)) || StringUtils.isNotBlank(line.get(weekDay))) {
                        lineInMap.put(weekDay, "x");
                    }
                }
            }
        }

        for (String lineId : map.keySet()) {
            Map<ColumnIdentifier, String> line = map.get(lineId);
            String name = line.get(requestsLayout.ID);
            try {
                Periodicity periodicity = new Periodicity();
                for (requestsLayout weekDay : requestsLayout.weekDays) {
                    periodicity.setWeekDay(requestsLayout.getWeekDay(weekDay), StringUtils.isNotBlank(line.get(weekDay)));
                }

                String cleanLine = new StringBuilder()
                        .append(line.get(requestsLayout.ID))
                        .append(";")
                        .append(line.get(requestsLayout.MON))
                        .append(";")
                        .append(line.get(requestsLayout.TUE))
                        .append(";")
                        .append(line.get(requestsLayout.WED))
                        .append(";")
                        .append(line.get(requestsLayout.THU))
                        .append(";")
                        .append(line.get(requestsLayout.FRI))
                        .append(";")
                        .append(line.get(requestsLayout.SAT))
                        .append(";")
                        .append(line.get(requestsLayout.SUN))
                        .append(";")
                        .append(line.get(requestsLayout.FROM))
                        .append(";")
                        .append(line.get(requestsLayout.TO))
                        .append(";")
                        .append(line.get(requestsLayout.DEPTIME))
                        .append(";")
                        .append(line.get(requestsLayout.ARRTIME))
                        .append(";")
                        .toString();
                LOGGER.info(cleanLine);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Skipping request " + name + " since it has unknown to/from node", e);
                continue;
            }
        }

        return simpleTrainPathApplications;
    }

    /**
     * Read the requests from the input file and filter them.
     *
     * @param macroscopicTopology
     * @param requestFilterLower
     * @param requestFilterUpper
     * @param clean
     * @param ignoreCompletelyAllocatedRequests
     * @return
     */
    public List<TrainPathApplication> readRequests(MacroscopicTopology macroscopicTopology, Periodicity requestFilterLower, Periodicity requestFilterUpper, boolean clean, boolean ignoreCompletelyAllocatedRequests, int hardMaximumEarlierDeparture, int hardMinimumDwellTime, int hardMaximumLaterArrival) {
        List<TrainPathApplication> requests = new LinkedList<TrainPathApplication>();


        String colLayoutString = getPropertyValue(tpaProps.REQUESTS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(requestsLayout.values().length);
        for (requestsLayout l : requestsLayout.values()) {
            cols.add(l);
        }
        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);

        if (clean) {
            List<Integer> colsToDelete = new LinkedList<>();
            colsToDelete.add(colLayoutMapping.get(requestsLayout.MON));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.TUE));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.WED));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.THU));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.FRI));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.SAT));
            colsToDelete.add(colLayoutMapping.get(requestsLayout.SUN));
            resetRequestAllocations(getPropertyValue(tpaProps.REQUESTS_WS_NAME), Integer.parseInt(getPropertyValue(tpaProps.REQUESTS_WS_HEADER_ROWS)), colsToDelete);
        }
        String wsName = getPropertyValue(tpaProps.REQUESTS_WS_NAME);
        int headerRowsNb = Integer.parseInt(getPropertyValue(tpaProps.REQUESTS_WS_HEADER_ROWS));
        List<Map<ColumnIdentifier, String>> lines = readWorksheet(wsName, headerRowsNb, colLayoutMapping);

        // Map is used to generate requests from current allocations
        Map<String, Map<ColumnIdentifier, String>> map = new HashMap<String, Map<ColumnIdentifier, String>>();
        for (Map<ColumnIdentifier, String> line : lines) {
            String requestNr = line.get(requestsLayout.ID);
            if (StringUtils.isBlank(requestNr)) {
                continue;
            }
            if (StringUtils.isBlank(line.get(requestsLayout.FROM))) {
                LOGGER.warn(TpaParser.corrupt_input, "Request " + requestNr + " has empty start node!");
                continue;
            }
            if (StringUtils.isBlank(line.get(requestsLayout.TO))) {
                LOGGER.warn(TpaParser.corrupt_input, "Request " + requestNr + " has empty end node!");
                continue;
            }
            if (StringUtils.isBlank(line.get(requestsLayout.DEPTIME))) {
                LOGGER.warn(TpaParser.corrupt_input, "Request " + requestNr + " has empty departure time!");
                continue;
            }
            if (StringUtils.isBlank(line.get(requestsLayout.ARRTIME))) {
                LOGGER.warn(TpaParser.corrupt_input, "Request " + requestNr + " has empty arrival time!");
                continue;
            }
            String name = line.get(requestsLayout.ID);
            try {
                SystemNode from = macroscopicTopology.getSystemNode(getUniqueSystemNode(line.get(requestsLayout.FROM)));
                SystemNode to = macroscopicTopology.getSystemNode(getUniqueSystemNode(line.get(requestsLayout.TO)));

                if (from.equals(to)) {
                    throw new IllegalArgumentException("From (" + from + ") and to node (" + to + ") must not be the same.");
                }

                LocalTime startTime = LocalTime.parse(line.get(requestsLayout.DEPTIME));
                LocalTime endTime = LocalTime.parse(line.get(requestsLayout.ARRTIME));
                Periodicity periodicity = new Periodicity();
                for (requestsLayout weekDay : requestsLayout.weekDays) {
                    periodicity.setWeekDay(requestsLayout.getWeekDay(weekDay), getPropertyValue(tpaProps.REQUESTS_REQUESTED_DAY_MARKER).equals(line.get(weekDay)));
                }

                // Add request if contained in filter bounds
                if (ignoreCompletelyAllocatedRequests && periodicity.getWeekDays().size() == 0) {
                    LOGGER.debug("Filtered out request " + name + " since " + periodicity.getStringRepresentation() + " no unallocated day.");
                    continue;
                }
                if (periodicity.containedWithin(requestFilterLower, requestFilterUpper)) {
                    TrainPathApplication r = new TrainPathApplication(name, from, to, startTime, endTime, periodicity, hardMaximumEarlierDeparture, hardMinimumDwellTime, hardMaximumLaterArrival);
                    requests.add(r);
                    LOGGER.debug("Filtered in request " + r.getName() + " since " + periodicity.getStringRepresentation() + " in [" + requestFilterLower.getStringRepresentation() + "," + requestFilterUpper.getStringRepresentation() + "]");
                } else {
                    LOGGER.debug("Filtered out request " + name + " since " + periodicity.getStringRepresentation() + " not in [" + requestFilterLower.getStringRepresentation() + "," + requestFilterUpper.getStringRepresentation() + "]");
                }

            } catch (IllegalArgumentException e) {
                LOGGER.warn(TpaParser.corrupt_input, "Skipping request " + name, e);
                continue;
            }


        }
        return requests;

    }

    private void allocate(SimpleTrainPathApplication simpleTrainPathApplication, SolutionCandidate allocation) {
        String colLayoutString = getPropertyValue(tpaProps.TRAINPATHS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(trainPathLayout.values().length);
        for (trainPathLayout l : trainPathLayout.values()) {
            cols.add(l);
        }

        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);
        int rowsFrom = Integer.parseInt(getPropertyValue(tpaProps.TRAINPATHS_WS_HEADER_ROWS));

        for (TrainPathSlot trainPathSlot : allocation.getPath()) {
            Sheet sheet = wb.getSheet(trainPathSlot.getPeriodicalTrainPathSlot().getTrainPathSectionName());

            for (int i = rowsFrom; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String slotId = getCellValueString(row.getCell(colLayoutMapping.get(trainPathLayout.ID)));
                if (trainPathSlot.getPeriodicalTrainPathSlot().getName().equals(slotId)) {
                    trainPathLayout day = trainPathLayout.getWeekDayTrainPathLayout(trainPathSlot.getStartTime().getDayOfWeek());
                    String slotName = trainPathSlot.getName();
                    int colNum = colLayoutMapping.get(day);
                    Cell cell = row.getCell(colNum, Row.CREATE_NULL_AS_BLANK);
                    if (StringUtils.isNotBlank(getCellValueString(cell))) {
                        throw new IllegalStateException("Cell must be empty; trying to allocate " + slotName + " on " + day + " to request " + simpleTrainPathApplication.getName() + "; cell value is " + getCellValueString(cell));
                    }
                    cell.setCellValue(simpleTrainPathApplication.getName()); // TODO show periodicity here when implementing "non-flat" allocation
                    LOGGER.debug("Allocating " + slotName + " on " + day + " by request " + simpleTrainPathApplication.getName() + " of weight " + allocation.getWeight());
                }
            }
        }


    }

    /**
     * Mark the request as allocated on all days of the request.
     *
     * @param request
     */
    private void markRequestAllocated(TrainPathApplication request) {
        String colLayoutString = getPropertyValue(tpaProps.REQUESTS_COL_LAYOUT);
        List<ColumnIdentifier> cols = new ArrayList<ColumnIdentifier>(requestsLayout.values().length);
        for (requestsLayout l : requestsLayout.values()) {
            cols.add(l);
        }

        Map<ColumnIdentifier, Integer> colLayoutMapping = getColLayoutMapping(colLayoutString, cols);
        int rowsFrom = Integer.parseInt(getPropertyValue(tpaProps.REQUESTS_WS_HEADER_ROWS));
        Sheet sheet = wb.getSheet(getPropertyValue(tpaProps.REQUESTS_WS_NAME));

        for (int i = rowsFrom; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(colLayoutMapping.get(requestsLayout.ID)) != null) {
                String allocatedRequest = getCellValueString(row.getCell(colLayoutMapping.get(requestsLayout.ID)));
                if (allocatedRequest.equals(request.getName())) {
                    for (Integer day : request.getPeriodicity().getWeekDays()) {
                        requestsLayout requestDay = requestsLayout.getWeekDayTrainPathLayout(day);
                        int colNum = colLayoutMapping.get(requestDay);

                        Cell cell = row.getCell(colNum, Row.CREATE_NULL_AS_BLANK);
                        if (!getCellValueString(cell).equals(getPropertyValue(tpaProps.REQUESTS_REQUESTED_DAY_MARKER))) {
                            throw new IllegalStateException("Application " + request.getName() + " on day " + day + " must have requested flag \"" + getPropertyValue(tpaProps.REQUESTS_REQUESTED_DAY_MARKER) + "\" since we're trying to mark it satisfied; found " + getCellValueString(cell));
                        }
                        cell.setCellValue(getPropertyValue(tpaProps.REQUESTS_ALLOCATED_DAY_MARKER));
                        LOGGER.debug("Set allocated flag for  " + request.getName() + " on day " + day);
                    }
                }
            }
        }
    }

    /**
     * Mark the requests allocated in the output file on the allocated days.
     *
     * @param allocations
     */
    public void allocate(Map<SimpleTrainPathApplication, SolutionCandidate> allocations) {
        Map<String, SimpleTrainPathApplication> test = new HashMap<>();
        for (SimpleTrainPathApplication simpleTrainPathApplication : allocations.keySet()) {
            String name = simpleTrainPathApplication.getName();
            if (test.containsKey(name)) {
                throw new IllegalStateException("Double entry " + simpleTrainPathApplication.getName());
            } else {
                test.put(name, simpleTrainPathApplication);
            }
        }
        Set<TrainPathApplication> periodicalTrainPathApplicationWithPeriodicities = new HashSet<>();
        for (SimpleTrainPathApplication simpleTrainPathApplication : allocations.keySet()) {
            periodicalTrainPathApplicationWithPeriodicities.add(simpleTrainPathApplication.getParent());
        }
        Set<TrainPathApplication> removedPeriodicalTrainPathApplicationWithPeriodicities = new HashSet<>();

        for (TrainPathApplication trainPathApplication : periodicalTrainPathApplicationWithPeriodicities) {
            for (SimpleTrainPathApplication simpleTrainPathApplication : trainPathApplication.getChildren()) {
                if (!allocations.containsKey(simpleTrainPathApplication)) {
                    removedPeriodicalTrainPathApplicationWithPeriodicities.add(trainPathApplication);
                }
            }
        }
        for (TrainPathApplication trainPathApplication : periodicalTrainPathApplicationWithPeriodicities) {
            if (removedPeriodicalTrainPathApplicationWithPeriodicities.contains(trainPathApplication)) {
                LOGGER.error("No allocation for all days of " + trainPathApplication.getName() + " have an allocation: " + trainPathApplication.getPeriodicity().getStringRepresentation());
                throw new IllegalStateException("No allocation for all days of " + trainPathApplication.getName() + " have an allocation: " + trainPathApplication.getPeriodicity().getStringRepresentation());
            }
            for (SimpleTrainPathApplication simpleTrainPathApplication : trainPathApplication.getChildren()) {
                allocate(simpleTrainPathApplication, allocations.get(simpleTrainPathApplication));
            }
            markRequestAllocated(trainPathApplication);
        }
    }

    public enum tpaProps implements ColumnIdentifier {
        TRAINPATHSECTIONS_WS_NAME("Name of the train paths worksheet"),
        TRAINPATHSECTIONS_WS_HEADER_ROWS("Number of header rows in the train path sections worksheet"),
        TRAINPATHSECTIONS_COL_LAYOUT("Column layout of the train path sections worksheets, eg. \"ID,FROM,TO\""),
        TRAINPATHSECTION_NODES_TO_SYSTEM_NODES_MAPPING_WS_NAME("Name of worksheet mapping start/end nodes to system nodes"),
        TRAINPATHS_COL_LAYOUT("Column layout of the trainpaths worksheets, eg. \"ID,FROM,TO,MON,TUE,WED,THU,FRI,SAT,SUN\""),
        TRAINPATHS_WS_HEADER_ROWS("Number of header rows in the train paths worksheet"),
        TRAINPATHS_NO_OPERATING_DAY_MARKER("Marker used in the train path sheets to denote a no-operating day, e.g. \"x\""),
        REQUESTS_ALLOCATED_DAY_MARKER("Marker used in the requests sheet to denote a day already allocated, e.g. \"A\""),
        REQUESTS_REQUESTED_DAY_MARKER("Marker used in the requests sheet to denote a requested but not yet allocated, e.g. \"x\""),
        REQUESTS_WS_NAME("Name of the requests worksheet"),
        REQUESTS_WS_HEADER_ROWS("Number of header rows in the requests worksheet"),
        REQUESTS_WS_HEADER_ROW("Number of the header row in the requests worksheet (Excel counting, starting at 1)"),
        REQUESTS_COL_LAYOUT("Column layout of the requests worksheet, eg. \"ID,MON,TUE,WED,THU,FRI,SAT,SUN,FROM,TO,DEPTIME,ARRTIME\""),
        TERMINALSYSTEMNODES("Comma-separated list of end nodes ot the macroscopic topology spanning the topology, e.g. \"Basel,Chiasso,Genf,Scuol,Tirano\"");


        private final String description;

        tpaProps(String description) {
            this.description = description;
        }

    }

    public enum trainPathLayout implements ColumnIdentifier {
        ID, DEPTIME, ARRTIME, MON, TUE, WED, THU, FRI, SAT, SUN;

        public static final List<trainPathLayout> weekDays = Collections.unmodifiableList(Arrays.asList(MON, TUE, WED, THU, FRI, SAT, SUN));

        public static trainPathLayout getWeekDayTrainPathLayout(int i) {
            switch (i) {
                case MONDAY:
                    return trainPathLayout.MON;
                case TUESDAY:
                    return trainPathLayout.TUE;
                case WEDNESDAY:
                    return trainPathLayout.WED;
                case THURSDAY:
                    return trainPathLayout.THU;
                case FRIDAY:
                    return trainPathLayout.FRI;
                case SATURDAY:
                    return trainPathLayout.SAT;
                case SUNDAY:
                    return trainPathLayout.SUN;
                default:
                    throw new IllegalArgumentException("Illegal week day " + i);
            }
        }

        public static int getWeekDay(trainPathLayout l) {
            switch (l) {
                case MON:
                    return MONDAY;
                case TUE:
                    return TUESDAY;
                case WED:
                    return WEDNESDAY;
                case THU:
                    return THURSDAY;
                case FRI:
                    return FRIDAY;
                case SAT:
                    return SATURDAY;
                case SUN:
                    return SUNDAY;
                default:
                    throw new IllegalArgumentException("Illegal week day train path column name " + l);
            }
        }
    }

    public enum requestsLayout implements ColumnIdentifier {
        ID, FROM, TO, DEPTIME, ARRTIME, MON, TUE, WED, THU, FRI, SAT, SUN;
        public static final List<requestsLayout> weekDays = Collections.unmodifiableList(Arrays.asList(MON, TUE, WED, THU, FRI, SAT, SUN));

        public static int getWeekDay(requestsLayout l) {
            switch (l) {
                case MON:
                    return MONDAY;
                case TUE:
                    return TUESDAY;
                case WED:
                    return WEDNESDAY;
                case THU:
                    return THURSDAY;
                case FRI:
                    return FRIDAY;
                case SAT:
                    return SATURDAY;
                case SUN:
                    return SUNDAY;
                default:
                    throw new IllegalArgumentException("Illegal week day train path column name " + l);
            }
        }

        public static requestsLayout getWeekDayTrainPathLayout(int i) {
            switch (i) {
                case MONDAY:
                    return requestsLayout.MON;
                case TUESDAY:
                    return requestsLayout.TUE;
                case WEDNESDAY:
                    return requestsLayout.WED;
                case THURSDAY:
                    return requestsLayout.THU;
                case FRIDAY:
                    return requestsLayout.FRI;
                case SATURDAY:
                    return requestsLayout.SAT;
                case SUNDAY:
                    return requestsLayout.SUN;
                default:
                    throw new IllegalArgumentException("Illegal week day " + i);
            }
        }
    }

    public enum trainPathSectionLayout implements ColumnIdentifier {
        ID, FROM, TO
    }

    public interface ColumnIdentifier {
        String name();
    }

}
