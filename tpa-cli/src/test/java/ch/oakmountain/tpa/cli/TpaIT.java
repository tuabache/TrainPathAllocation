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

package ch.oakmountain.tpa.cli;

import ch.oakmountain.tpa.parser.TpaParser;
import ch.oakmountain.tpa.solver.*;
import gurobi.GRBException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import static ch.oakmountain.tpa.cli.TpaCLI.capsule;
import static org.junit.Assert.*;


/**
 *
 */
@RunWith(Parameterized.class)
public class TpaIT {

    private static final Logger LOGGER = LogManager.getLogger();


    private static final String TEMPLATE_TINY = "TpaTestDataEmptyTemplateTiny.xls";
    private static final String TEMPLATE_LARGE = "TpaTestDataEmptyTemplate.xls";
    private static final String skipweboutput = "";//"-skipweboutput";

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private final String pathbased;
    private final Set<String> names1;
    private final Set<String> names2;


    @Rule
    // Log into output directory
    public TestRule watcher = new TestWatcher() {

        private String myFile = "myFile";

        private String getOutputDirName(Description description) {
            String suffix = getModelTypeSuffix();
            String cleanMethodName = description.getMethodName().split(Pattern.quote("["))[0];
            return "output" + File.separator + cleanMethodName + "_" + suffix;
        }

        private String getTempLogFileName(Description description) {
            String suffix = getModelTypeSuffix();
            String cleanMethodName = description.getMethodName().split(Pattern.quote("["))[0];
            return "tpa-" + cleanMethodName + "-" + suffix + ".log";
        }

        @Override
        protected void starting(Description description) {
            // https://logging.apache.org/log4j/2.x/manual/customconfig.html
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final PatternLayout layout = PatternLayout.newBuilder().withPattern("%d %p %marker %c{1.} [%t] %m %ex%n").build();

            Appender appender = FileAppender.createAppender(getTempLogFileName(description), "false", "false", myFile, "true", "false", "false", "4000", layout, null, "false", null, config);
            appender.start();
            config.addAppender(appender);
            final Level level = null;
            final Filter filter = null;
            for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
                loggerConfig.addAppender(appender, level, filter);
            }
        }

        @Override
        protected void finished(Description description) {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
                loggerConfig.removeAppender(myFile);
            }
            try {
                File destFile = new File(getOutputDirName(description) + File.separator + "tpa.log");
                if (destFile.exists()) {
                    FileUtils.forceDelete(destFile);
                }
                FileUtils.moveFile(new File(getTempLogFileName(description)), destFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    };

    public TpaIT(String pathbased) {
        this.pathbased = pathbased;

        // Allow only for request between {A1,B1} x {C1,D1}
        names1 = new HashSet<>();
        names1.add("A1");
        names1.add("B1");
        names2 = new HashSet<>();
        names2.add("C1");
        names2.add("D1");
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {""}, {"-pathbased"}});
    }

    /**
     * Data set 1: a single daily request on a line, flat.
     *
     * @throws CycleDetectedException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws IOException
     */
    @Test
    public void integrationTest1() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        ITCapsule ITCapsule = new ITCapsule("TpaTestData.xls").invoke(0, 5, 60);
        List<TrainPathApplication> requests = ITCapsule.getRequests();
        TrainPathSlotCatalogue trainPathSlotCatalogue = ITCapsule.getTrainPathSlotCatalogue();


        assertTrue(requests.size() == 1);
        assertEquals(0, requests.get(0).getPeriodicity().getWeekDays().size());

        // all requests must take the earliest slot at every section (i.e. the slot must not appear in the train path catalogue any more)
        for (int i = 1; i <= 7; i++) {
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot
                    ("S23_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S3409-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S4509-002_" + i));
        }
    }

    private String getOutputDirName() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : st) {
            if (stackTraceElement.getMethodName().startsWith("integration")) {
                String suffix = getModelTypeSuffix();
                return "output" + File.separator + stackTraceElement.getMethodName() + "_" + suffix;
            }
        }
        return null;
    }


    private String getModelTypeSuffix() {
        String suffix;
        if (StringUtils.isNotBlank(this.pathbased)) {
            suffix = "pathbased";
        } else {
            suffix = "arcnode";
        }
        return suffix;
    }

    /**
     * Data set 1: a single Mon-only request on a line, flat.
     *
     * @throws CycleDetectedException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws IOException
     */
    @Test
    public void integrationTest1MonTueOnly() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        ITCapsule ITCapsule = new ITCapsule("TpaTestData_MonTueonly.xls").invoke(0, 5, 60);
        List<TrainPathApplication> requests = ITCapsule.getRequests();
        TrainPathSlotCatalogue trainPathSlotCatalogue = ITCapsule.getTrainPathSlotCatalogue();


        assertTrue(requests.size() == 1);
        assertEquals(0, requests.get(0).getPeriodicity().getWeekDays().size());

        // all requests must take the earliest slot at every section (i.e. the slot must not appear in the train path catalogue any more)
        for (int i = 1; i <= 2; i++) {
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot
                    ("S23_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S3409-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S4509-002_" + i));
        }
    }


    /**
     * Data set 2: two daily requests at the same time, flat.
     *
     * @throws CycleDetectedException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws IOException
     */
    @Test
    public void integrationTest2() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        ITCapsule ITCapsule = new ITCapsule("TpaTestData2.xls").invoke(0, 5, 60);
        List<TrainPathApplication> requests = ITCapsule.getRequests();
        TrainPathSlotCatalogue trainPathSlotCatalogue = ITCapsule.getTrainPathSlotCatalogue();


        assertTrue(requests.size() == 2);
        // both request have been allocated
        assertEquals(0, requests.get(0).getPeriodicity().getWeekDays().size());
        assertEquals(0, requests.get(1).getPeriodicity().getWeekDays().size());

        // all requests must take the earliest two slots at every section (i.e. the slot must not appear in the train path catalogue any more)
        for (int i = 1; i <= 7; i++) {
            // first slot, taken by either of them
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S23_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S3409-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S4509-002_" + i));

            // second slot, taken by either of them
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S2309-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S3409-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S4510-001_" + i));
        }
    }

    /**
     * Data set 3: two requests at the same time, first one daily, second one Mon-Tue only, flat.
     *
     * @throws CycleDetectedException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws IOException
     */
    @Test
    public void integrationTest3() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        ITCapsule ITCapsule = new ITCapsule("TpaTestData3.xls").invoke(0, 5, 60);
        List<TrainPathApplication> requests = ITCapsule.getRequests();
        TrainPathSlotCatalogue trainPathSlotCatalogue = ITCapsule.getTrainPathSlotCatalogue();


        assertTrue(requests.size() == 2);
        // both request have been fully allocated
        assertEquals(0, requests.get(0).getPeriodicity().getWeekDays().size());
        assertEquals(0, requests.get(1).getPeriodicity().getWeekDays().size());

        // all requests must take the earliest two slots at every section (i.e. the slot must not appear in the train path catalogue any more)
        for (int i = 1; i <= 7; i++) {
            // first slot, taken by either of them
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S23_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S34_09-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S45_09-002_" + i));

            if (i <= 2) {
                // second slot, taken by either of them
                assertNull(trainPathSlotCatalogue.getSlot("S12_08-002_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S23_09-001_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S34_09-002_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S45_10-001_" + i));
            } else {
                // second slot, taken by neither of them
                assertNotNull(trainPathSlotCatalogue.getSlot("S12_08-002_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S23_09-001_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S34_09-002_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S45_10-001_" + i));
            }
        }
    }

    /**
     * Data set 4: two requests at shifted, first one daily, second one Mon-Tue only, flat.
     *
     * @throws CycleDetectedException
     * @throws IllegalAccessException
     * @throws ParseException
     * @throws IOException
     */
    @Test
    public void integrationTest4() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        ITCapsule itCapsule = new ITCapsule("TpaTestData4.xls").invoke(0, 5, 60);
        List<TrainPathApplication> requests = itCapsule.getRequests();
        TrainPathSlotCatalogue trainPathSlotCatalogue = itCapsule.getTrainPathSlotCatalogue();


        assertTrue(requests.size() == 2);
        // both request have been fully allocated
        assertEquals(0, requests.get(0).getPeriodicity().getWeekDays().size());
        assertEquals(0, requests.get(1).getPeriodicity().getWeekDays().size());

        // all requests must take the earliest two slots at every section (i.e. the slot must not appear in the train path catalogue any more)
        for (int i = 1; i <= 7; i++) {
            // first slot, taken by first
            assertNull(trainPathSlotCatalogue.getSlot("S12_08-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S23_08-002_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S34_09-001_" + i));
            assertNull(trainPathSlotCatalogue.getSlot("S45_09-002_" + i));

            if (i <= 2) {
                // second slot, taken by second on Mon, Tue
                assertNull(trainPathSlotCatalogue.getSlot("S12_08-002_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S23_09-001_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S34_09-002_" + i));
                assertNull(trainPathSlotCatalogue.getSlot("S451_0-001_" + i));
            } else {
                // second slot, taken by neither of them
                assertNotNull(trainPathSlotCatalogue.getSlot("S12_08-002_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S23_09-001_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S34_09-002_" + i));
                assertNotNull(trainPathSlotCatalogue.getSlot("S45_10-001_" + i));
            }
        }
    }

    @Test
    public void integrationTestGeneratedEarliness() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, GRBException {
        try {
            TrainPathAllocations results = runIntegrationTestGenerated(90, 4, 60, 5, 60, TEMPLATE_TINY);
            TrainPathAllocationProblem tpa = results.getTpa();
            Integer previous = null;
            SimpleTrainPathApplication previousSimpleTrainPathApplication = null;
            // Every hour on every day, the total weight of the allocated paths should be the same: we're at capacity, so every slot in the bottleneck section will be taken
            Map<SimpleTrainPathApplication, SolutionCandidate> allocations = results.getAllocations();
            for (int day = 1; day <= 7; day++) {
                for (int hour = 0; hour < 24; hour++) {
                    double sum1 = 0;
                    double sum2 = 0;
                    for (SimpleTrainPathApplication simpleTrainPathApplication : allocations.keySet()) {
                        if (simpleTrainPathApplication.getStartTime().getHourOfDay() == hour && simpleTrainPathApplication.getStartTime().getDayOfWeek() == day && names1.contains(simpleTrainPathApplication.getFrom().getName())) {
                            sum1 = sum1 + allocations.get(simpleTrainPathApplication).getWeight();
                        }
                        if (simpleTrainPathApplication.getStartTime().getHourOfDay() == hour && simpleTrainPathApplication.getStartTime().getDayOfWeek() == day && names2.contains(simpleTrainPathApplication.getFrom().getName())) {
                            sum2 = sum2 + allocations.get(simpleTrainPathApplication).getWeight();
                        }
                    }
                    // 1. .00, arrival 1.20 => duration 80 (48)
                    // 2. .15, arrival 1.35 => duration 80 + 5 lateness (51)
                    // 3. .30, arrval 1.50 => duration 80 + 20 lateness (60)
                    // 4. .45, arrival 2.05 => duration 80 + 15 earliness (57)
                    assertEquals("Day=" + day + ", hour=" + hour, (80 + 85 + 100 + 95) * 60000, (int) sum1);
                    assertEquals("Day=" + day + ", hour=" + hour, (80 + 85 + 100 + 95) * 60000, (int) sum2);
                }
            }
        } catch (InfeasibleTPAException e) {
            fail("Problem infeasible " + e);
        }
    }

    @Test
    public void integrationTestGeneratedNoEarliness() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, GRBException {
        try {
            TrainPathAllocations results = runIntegrationTestGenerated(90, 4, 0, 5, 60, TEMPLATE_TINY);
            TrainPathAllocationProblem tpa = results.getTpa();
            Integer previous = null;
            SimpleTrainPathApplication previousSimpleTrainPathApplication = null;
            // Every hour on every day, the total weight of the allocated paths should be the same: we're at capacity, so every slot in the bottleneck section will be taken
            Map<SimpleTrainPathApplication, SolutionCandidate> allocations = results.getAllocations();
            for (int day = 1; day <= 7; day++) {
                for (int hour = 0; hour < 24; hour++) {
                    double sum1 = 0;
                    double sum2 = 0;
                    for (SimpleTrainPathApplication simpleTrainPathApplication : allocations.keySet()) {
                        if (simpleTrainPathApplication.getStartTime().getHourOfDay() == hour && simpleTrainPathApplication.getStartTime().getDayOfWeek() == day && names1.contains(simpleTrainPathApplication.getFrom().getName())) {
                            sum1 = sum1 + allocations.get(simpleTrainPathApplication).getWeight();
                        }
                        if (simpleTrainPathApplication.getStartTime().getHourOfDay() == hour && simpleTrainPathApplication.getStartTime().getDayOfWeek() == day && names2.contains(simpleTrainPathApplication.getFrom().getName())) {
                            sum2 = sum2 + allocations.get(simpleTrainPathApplication).getWeight();
                        }
                    }
                    // no earlyness possible, but late departure >= 45!
                    // 1. .00, arrival 1.20 => duration 80 (48)
                    // 2. .15, arrival 1.35 => duration 80 + 5 lateness (51)
                    // 3. .30, arrval 1.50 => duration 80 + 20 lateness (60)
                    // 4. .45, arrival 2.05 => duration 80 + 35 lateness (69)
                    assertEquals("Day=" + day + ", hour=" + hour, (80 + 85 + 100 + 115) * 60000, (int) sum1);
                    assertEquals("Day=" + day + ", hour=" + hour, (80 + 85 + 100 + 115) * 60000, (int) sum2);
                }
            }
        } catch (InfeasibleTPAException e) {
            fail("Problem infeasible " + e);
        }
    }

    private TrainPathAllocations runIntegrationTestGenerated(int requestDuration, int nbSlotsPerHour, int globalHardMaximumEarlierDeparture, int globalHardMinimumDwellTime, int globalHardMaximumLaterArrival, String templateFile) throws IOException, IllegalAccessException, CycleDetectedException, InfeasibleTPAException, GRBException {
        // Generate test data
        String outputDir = getOutputDirName();
        String outFileName = outputDir + File.separator + "TpaTestDataGenerated.xls";
        generateTestDataFromTemplate(outputDir, outFileName, templateFile, nbSlotsPerHour, requestDuration);

        // Make full analysis
        String[] args2 = new String[]{
                "-file", outFileName,
                "-output", outputDir,
                "-properties", getClass().getResource("/testProperties2").getPath(),
                skipweboutput,
                pathbased,
                "-requestfilter", "???????",
                "-globalHardMaximumEarlierDeparture", "" + globalHardMaximumEarlierDeparture,
                "-globalHardMinimumDwellTime", "" + globalHardMinimumDwellTime,
                "-globalHardMaximumLaterArrival", "" + globalHardMaximumLaterArrival
        };
        TrainPathAllocations results = TpaCLI.doMain(args2);
        return results;
    }

    @Test
    public void integrationDagConstruction() throws IOException {
        String outputDir = getOutputDirName();
        String[] args = {
                "-file", TpaIT.class.getResource("/TpaTestDataEmptyTemplateTiny.xls").getPath(),
                "-output", outputDir,
                "-properties", TpaIT.class.getResource("/testProperties2").getPath(),
                "-cleanoutput",
                skipweboutput,
                pathbased,
                "-requestfilter", "1111111",
                "-globalHardMaximumLaterArrival", "60",
                "-globalHardMinimumDwellTime", "5"
        };
        TpaCLI.TpaCLIPropertiesCapsule capsule = capsule(args);
        TpaParser tpaParser = capsule.getTpaParser();
        MacroscopicTopology macroscopicTopology = tpaParser.readMacroscopicTopology();
    }

    @Test
    public void integrationTestGeneratedConflict() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        // http://stackoverflow.com/questions/156503/how-do-you-assert-that-a-certain-exception-is-thrown-in-junit-4-tests
        exception.expect(InfeasibleTPAException.class);
        TrainPathAllocations results = runIntegrationTestGenerated(3 * 30, 3, 0, 5, 60, TEMPLATE_TINY);
    }


    /**
     * Generate slots of 20 minutes over whole day and week for all sections and 1 request per hour between the leaf nodes.
     *
     * @param outputDir
     * @param outFileName
     * @param testDataFileName
     * @param slotsPerHour
     * @param requestLength
     * @throws IOException
     */
    private void generateTestDataFromTemplate(String outputDir, String outFileName, String testDataFileName, int slotsPerHour, int requestLength) throws IOException {
        String[] args = {
                "-file", getClass().getResource("/" + testDataFileName).getPath(),
                "-output", outputDir,
                "-properties", getClass().getResource("/testProperties2").getPath(),
                "-cleanoutput",
                skipweboutput,
                pathbased,
                "-requestfilter", "1111111"
        };

        TpaCLI.TpaCLIPropertiesCapsule capsule = capsule(args);
        TpaParser tpaParser = capsule.getTpaParser();
        MacroscopicTopology macroscopicTopology = tpaParser.readMacroscopicTopology();

        // catalogue: slotsPerHour per hour, slot duration 20 minutes
        TrainPathSlotCatalogue catalogue = TrainPathSlotCatalogue.generateTestTrainPathCatalogue(macroscopicTopology, slotsPerHour, 20);
        tpaParser.addCatalogue(macroscopicTopology, catalogue);
        List<SystemNode> endNodes = tpaParser.getTerminalSystemNodes(macroscopicTopology);


        for (SystemNode from : endNodes) {
            for (SystemNode to : endNodes) {
                if (test(from, to, names1, names2)) {
                    // one request per hour, every day; allow for 30 minutes for all 17 slots between the two end nodes
                    // should be feasible since we have 4 slots per hour and direction and 4 trains per hour and direction in the bottleneck nodes M1-M8
                    tpaParser.generateRequests(macroscopicTopology, 1, requestLength, 0, from.getName(), to.getName(), Periodicity.getWholeWeekPeriodicity());
                }
            }
        }

        tpaParser.writeFile(outFileName);
    }

    @Test
    public void integrationTestGeneratedLarge() throws CycleDetectedException, IllegalAccessException, ParseException, IOException, InfeasibleTPAException, GRBException {
        runIntegrationTestGenerated(17 * 30, 4, 0, 5, 60, TEMPLATE_LARGE);

        // TODO assertions
    }

    private boolean test(SystemNode from, SystemNode to, Set<String> names1, Set<String> names2) {
        return (names1.contains(from.getName()) && names2.contains(to.getName())) || (names1.contains(to.getName()) && names2.contains(from.getName()));
    }


    private class ITCapsule {
        private List<TrainPathApplication> requests;
        private TrainPathSlotCatalogue trainPathSlotCatalogue;
        private String fileName;

        public ITCapsule(String fileName) {
            this.fileName = fileName;
        }

        public List<TrainPathApplication> getRequests() {
            return requests;
        }

        public TrainPathSlotCatalogue getTrainPathSlotCatalogue() {
            return trainPathSlotCatalogue;
        }

        public ITCapsule invoke(int globalHardMaximumEarlierDeparture, int globalHardMinimumDwellTime, int globalHardMaximumLaterArrival) throws IOException, ParseException, CycleDetectedException, IllegalAccessException, InfeasibleTPAException, GRBException {
            // Make full analysis
            String outputDirName = getOutputDirName();
            String[] args = {
                    "-file", getClass().getResource("/" + fileName).getPath(),
                    "-output", outputDirName,
                    "-properties", getClass().getResource("/testProperties").getPath(),
                    "-clean",
                    "-cleanoutput",
                    skipweboutput,
                    pathbased,
                    "-requestfilter", "???????",
                    "-globalHardMaximumEarlierDeparture", "" + globalHardMaximumEarlierDeparture,
                    "-globalHardMinimumDwellTime", "" + globalHardMinimumDwellTime,
                    "-globalHardMaximumLaterArrival", "" + globalHardMaximumLaterArrival
            };
            TpaCLI.main(args);

            // Read in the results to see whether they meet our expectations
            String[] args2 = {
                    "-file", outputDirName + "/" + fileName,
                    "-output", outputDirName,
                    "-properties", getClass().getResource("/testProperties").getPath(),
                    "-requestfilter", "???????"
            };
            boolean clean = false;
            boolean ignoreCompletelyAllocatedRequests = false;
            TpaCLI.TpaCLIPropertiesCapsule capsule = capsule(args2);
            TpaParser tpaParser = capsule.getTpaParser();
            MacroscopicTopology macroscopicTopology = tpaParser.readMacroscopicTopology();
            Pair<Periodicity, Periodicity> patterns = Periodicity.parsePeriodicityBounds(Periodicity.allPattern);

            requests = tpaParser.readRequests(macroscopicTopology, patterns.first, patterns.second, clean, ignoreCompletelyAllocatedRequests, globalHardMaximumEarlierDeparture, globalHardMinimumDwellTime, globalHardMaximumLaterArrival);
            trainPathSlotCatalogue = tpaParser.readTrainPathCatalogue(macroscopicTopology, clean, true);
            return this;
        }
    }
}
