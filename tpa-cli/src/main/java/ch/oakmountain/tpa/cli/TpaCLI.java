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
import ch.oakmountain.tpa.web.TpaWebPersistor;
import com.google.common.base.Stopwatch;
import gurobi.GRBException;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 *
 */
public class TpaCLI {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Access to the tpa parser for integration tests.
     *
     * @param args
     * @return
     * @throws IOException
     */
    protected static TpaCLIPropertiesCapsule capsule(String[] args) throws IOException {
        TpaCLIPropertiesCapsule tpaCLIPropertiesCapsule = new TpaCLIPropertiesCapsule(args).setup();
        return tpaCLIPropertiesCapsule;
    }

    /**
     * The tpa CLI.
     *
     * @param args Expect one argument that capsuleSetupOnly the FILE to read.
     * @throws java.io.IOException When there capsuleSetupOnly an error processing the FILE.
     */
    public static void main(String[] args) throws CycleDetectedException, InfeasibleTPAException, IllegalAccessException, IOException, GRBException {
        doMain(args);
    }

    public static TrainPathAllocations doMain(String[] args) throws IOException, CycleDetectedException, IllegalAccessException, InfeasibleTPAException, GRBException {
        TpaCLIPropertiesCapsule tpaCLIPropertiesCapsule = new TpaCLIPropertiesCapsule(args).setup();
        if (tpaCLIPropertiesCapsule.capsuleSetupOnly()) return null;
        TpaParser tpaParser = tpaCLIPropertiesCapsule.getTpaParser();
        String outputDir = tpaCLIPropertiesCapsule.getOutputDir();
        boolean clean = tpaCLIPropertiesCapsule.isClean();
        boolean ignoreinfeasibleapps = tpaCLIPropertiesCapsule.isIgnoreinfeasibleapps();
        boolean skipweboutput = tpaCLIPropertiesCapsule.isSkipweboutput();
        boolean pathbased = tpaCLIPropertiesCapsule.isPathbased();
        String fileName = tpaCLIPropertiesCapsule.getFileName();
        Periodicity requestFilterLower = tpaCLIPropertiesCapsule.getRequestFilterLower();
        Periodicity requestFilterUpper = tpaCLIPropertiesCapsule.getRequestFilterUpper();

        int globalHardMaximumEarlierDeparture = tpaCLIPropertiesCapsule.getGlobalHardMaximumEarlierDeparture();
        int globalHardMinimumDwellTime = tpaCLIPropertiesCapsule.getGlobalHardMinimumDwellTime();
        int globalHardMaximumLaterArrival = tpaCLIPropertiesCapsule.getGlobalHardMaximumLaterArrival();

        try {
            Stopwatch stopwatchParseModel = TPAUtil.startStopWatch();

            // Parse macroscopic topology
            MacroscopicTopology macroscopicTopology = tpaParser.readMacroscopicTopology();
            if (!skipweboutput) {
                TpaWebPersistor.createGraph("topology", outputDir, macroscopicTopology.toCSV(), "<h1>Macroscopic Topology</h1>");
            }

            // Parse train path catalogue
            TrainPathSlotCatalogue trainPathSlotCatalogue = tpaParser.readTrainPathCatalogue(macroscopicTopology, clean, true); // TODO make cli option for correctTrainPathIds?
            LOGGER.info("Parsed " + trainPathSlotCatalogue.getNbPeriodicalSlots() + " periodical slots and " + trainPathSlotCatalogue.getNbSlots() + " slots ...");

            trainPathSlotCatalogue.logInfo();

            // Parse train path applications
            List<TrainPathApplication> periodicalTrainPathApplicationWithPeriodicities = tpaParser.readRequests(macroscopicTopology, requestFilterLower, requestFilterUpper, clean, true, globalHardMaximumEarlierDeparture, globalHardMinimumDwellTime, globalHardMaximumLaterArrival);
            LOGGER.info("Including the following " + periodicalTrainPathApplicationWithPeriodicities.size() + " unallocated requests...");
            for (TrainPathApplication r : periodicalTrainPathApplicationWithPeriodicities) {
                LOGGER.info("  Including request " + r.getName() + " on " + r.getNbDays() + " days: " + r.getPeriodicity().getStringRepresentation());
            }
            Set<SimpleTrainPathApplication> simpleTrainPathApplications = new HashSet<>();
            for (TrainPathApplication request : periodicalTrainPathApplicationWithPeriodicities) {
                for (Integer day : request.getPeriodicity().getWeekDays()) {
                    SimpleTrainPathApplication r = request.getRequestOnWeekDay(day);
                    simpleTrainPathApplications.add(r);
                }
            }
            TPAUtil.stopStopWatch(stopwatchParseModel, "PARSE MODEL");

            TrainPathAllocationProblem tpa = new TrainPathAllocationProblem(macroscopicTopology, simpleTrainPathApplications, trainPathSlotCatalogue);

            // Statistics?
            if (!skipweboutput) {
                Stopwatch stopwatchStats = TPAUtil.startStopWatch();
                TrainPathAllocationProblemModel.statistics(tpa, outputDir);
                TPAUtil.stopStopWatch(stopwatchStats, "COMPILING STATS");
            }

            TrainPathAllocationProblemModel tpaModel;
            if (pathbased) {
                tpaModel = new TrainPathAllocationProblemModelPathBased(tpa);
            } else {
                tpaModel = new TrainPathAllocationProblemModelArcNode(tpa);
            }

            TrainPathAllocations result = tpaModel.solve(outputDir, ignoreinfeasibleapps, true);

            tpaParser.allocate(result.getAllocations());

            // see the file and die
            File file = new File(fileName);
            String outFileName = outputDir + File.separator + file.getName();
            tpaParser.writeFile(outFileName);


            // TODO table with computation times?

            return result;

        } catch (InfeasibleTPAException e) {
            LOGGER.error(e);
            /*Map<String, GraphCSV> csvs = e.getCsvs();
            if (csvs != null) {
                for (String slotName : csvs.keySet()) {
                    TpaWebPersistor.createGraph(slotName, outputDir, csvs.get(slotName), "<h1>IIS Analysis for Slot" + slotName + "</h1>");
                }
            }*/
            throw e;
        }
    }


    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("tpa (train path allocation)", options);
    }

    private enum tpaOptions {
        CLEAN("clean", "start with empty allocation"),
        IGNOREINFEASIBLEAPPLICATIONS("ignoreinfeasibleapps", "ignore infeasible train path applications and try to allocation feasible applications"),
        PATHBASED("pathbased", "use path-based model, default is arc-node model"),
        SKIPWEBOUTPUT("skipweboutput", "do not create html pages (may save time)"),
        CLEANOUTPUT("cleanoutput", "delete dthe output dir"),
        HELP("help", "show this help message and terminate"),
        CONFIGHELP("confighelp", "show file format configuration options and terminate"),
        CONFIG("showconfig", "show effective file format configuration"),
        FILE("file", true, "file", "input file", true, null),
        PROPERTIES("properties", true, "file", "properties file (in ISO 8859-1 encoding)", true, null),
        OUTPUT("output", true, "file", "output directory", true, "output"),
        REQUESTFILTER("requestfilter", true, "pattern", "Pattern of the form 01??000", true, Periodicity.allPattern),
        ENUM_MAX_ITERATION("max_iter", true, "nb", "maximum iterations", true, "5"),
        GLOBALHARDMAXIMUMLATERARRIVAL("globalHardMaximumLaterArrival", true, "nb", "global hard maximum later arrival (minutes)", true, "0"),
        GLOBALHARDMAXIMUMEARLIERDEPARTURE("globalHardMaximumEarlierDeparture", true, "nb", "global hard maximum earlier departure (minutes)", true, "0"),
        GLOBALHARDMINIMUMDWELLTIME("globalHardMinimumDwellTime", true, "nb", "global hard minimum dwell time", true, "0");


        private final String description;
        private boolean hasArg = false;
        private String opt;
        private String argName;
        private boolean optionalArg = true;
        private String defaultValue = null;

        tpaOptions(String opt, boolean hasArg, String argName, String description, boolean optionalArg, String defaultValue) {
            this.opt = opt;
            this.hasArg = hasArg;
            this.argName = argName;
            this.description = description;
            this.optionalArg = optionalArg;
            this.defaultValue = defaultValue;
        }

        tpaOptions(String opt, String description) {
            this.opt = opt;
            this.description = description;
        }


        public String getOpt() {
            return opt;
        }

        public boolean isHasArg() {
            return hasArg;
        }

        public String getDescription() {
            if (!optionalArg) {
                return description + " (mandatory)";
            }
            if (StringUtils.isNotBlank(defaultValue)) {
                return description + "(default value: " + defaultValue + ")";
            }
            return description;
        }

        public String getArgName() {
            return argName;
        }


        @Override
        public String toString() {
            return opt;
        }

        public boolean isOptional() {
            return optionalArg;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }


    static class TpaCLIPropertiesCapsule {
        private boolean capsuleSetupOnly;
        private String[] args;
        private boolean clean;
        private boolean skipweboutput;
        private String fileName;
        private String outputDir;
        private Periodicity requestFilterLower;
        private Periodicity requestFilterUpper;
        private TpaParser tpaParser;
        private int dagEnumeration_maxIter;
        private int globalHardMaximumLaterArrival;
        private int globalHardMaximumEarlierDeparture;
        private int globalHardMinimumDwellTime;
        private boolean ignoreinfeasibleapps;
        private boolean pathbased;

        public TpaCLIPropertiesCapsule(String... args) {
            this.args = args;
        }

        public boolean isPathbased() {
            return pathbased;
        }

        public boolean isIgnoreinfeasibleapps() {
            return ignoreinfeasibleapps;
        }

        public boolean isSkipweboutput() {
            return skipweboutput;
        }

        public int getDagEnumeration_maxIter() {
            return dagEnumeration_maxIter;
        }

        boolean capsuleSetupOnly() {
            return capsuleSetupOnly;
        }

        public boolean isClean() {
            return clean;
        }

        public String getFileName() {
            return fileName;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public Periodicity getRequestFilterLower() {
            return requestFilterLower;
        }

        public Periodicity getRequestFilterUpper() {
            return requestFilterUpper;
        }

        public TpaParser getTpaParser() {
            return tpaParser;
        }

        public TpaCLIPropertiesCapsule setup() throws IOException {
            Options options = new Options();
            for (tpaOptions tpaOption : tpaOptions.values()) {
                if (tpaOption.isHasArg()) {
                    Option option
                            = OptionBuilder.withArgName(tpaOption.getArgName())
                            .hasArg()
                            .withDescription(tpaOption.getDescription())
                            .isRequired(!tpaOption.isOptional())
                            .create(tpaOption.getOpt());
                    options.addOption(option);
                } else {
                    options.addOption(tpaOption.getOpt(), tpaOption.getDescription());
                }


            }

            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine;
            try {
                commandLine = parser.parse(options, args);
            } catch (ParseException e) {
                printHelp(options);
                throw new RuntimeException("Error parsing arguments!", e);
            }


            if (commandLine.hasOption(tpaOptions.HELP.getOpt())) {
                printHelp(options);
                capsuleSetupOnly = true;
                return this;
            }

            InputStream in;

            // Load parser propertie
            String appPropertiesFileName;
            Properties applicationProps = new Properties();
            if (commandLine.hasOption(tpaOptions.PROPERTIES.getOpt())) {
                appPropertiesFileName = commandLine.getOptionValue(tpaOptions.PROPERTIES.getOpt());

            } else {
                printHelp(options);
                throw new IllegalArgumentException("Properties file has not been specified.");
            }
            if (Files.exists(FileSystems.getDefault().getPath(appPropertiesFileName))) {
                in = new FileInputStream(appPropertiesFileName);
                applicationProps.load(in);
                in.close();
            } else {
                throw new IllegalArgumentException("Cannot find FILE \"" + appPropertiesFileName + "\" passed to \"-" + tpaOptions.PROPERTIES.getOpt() + "\" or the default value \"" + tpaOptions.PROPERTIES.getDefaultValue() + "\"");
            }

            if (commandLine.hasOption(tpaOptions.CONFIGHELP.getOpt())) {
                PrintWriter pw = new PrintWriter(System.out); // NOSONAR
                for (TpaParser.tpaProps tpaProp : TpaParser.tpaProps.values()) {
                    pw.println(tpaProp.name() + "=" + applicationProps.getProperty(tpaProp.name()));

                }
                pw.flush();
                pw.close();
                capsuleSetupOnly = true;
                return this;
            }

            clean = commandLine.hasOption(tpaOptions.CLEAN.getOpt());
            ignoreinfeasibleapps = commandLine.hasOption(tpaOptions.IGNOREINFEASIBLEAPPLICATIONS.getOpt());


            //
            if (!commandLine.hasOption(tpaOptions.FILE.getOpt())) {
                printHelp(options);
                throw new IllegalArgumentException("Input file has not been specified.");
            } else {
                fileName = commandLine.getOptionValue(tpaOptions.FILE.getOpt());
            }
            if (commandLine.hasOption(tpaOptions.OUTPUT.getOpt())) {
                outputDir = commandLine.getOptionValue(tpaOptions.OUTPUT.getOpt());
            } else {
                outputDir = tpaOptions.OUTPUT.getDefaultValue();
            }
            Path outputDirPath = Paths.get(outputDir);
            if (!Files.exists(outputDirPath)) {
                try {
                    Files.createDirectories(outputDirPath);
                } catch (IOException e) {
                    LOGGER.error(e);
                    throw e;
                }
            }
            if (commandLine.hasOption(tpaOptions.CLEANOUTPUT.getOpt())) {
                FileUtils.deleteDirectory(new File(outputDir));
                Files.createDirectory(outputDirPath);
            }
            skipweboutput = commandLine.hasOption(tpaOptions.SKIPWEBOUTPUT.getOpt());
            pathbased = commandLine.hasOption(tpaOptions.PATHBASED.getOpt());

            Pair<Periodicity, Periodicity> periodicityBounds;
            if (commandLine.hasOption(tpaOptions.REQUESTFILTER.getOpt())) {
                periodicityBounds = Periodicity.parsePeriodicityBounds(commandLine.getOptionValue(tpaOptions.REQUESTFILTER.getOpt()));
            } else {
                periodicityBounds = Periodicity.parsePeriodicityBounds(tpaOptions.REQUESTFILTER.getDefaultValue());
            }
            requestFilterLower = periodicityBounds.first;
            requestFilterUpper = periodicityBounds.second;


            if (commandLine.hasOption(tpaOptions.ENUM_MAX_ITERATION.getOpt())) {
                dagEnumeration_maxIter = Integer.parseInt(commandLine.getOptionValue(tpaOptions.ENUM_MAX_ITERATION.getOpt()));
            } else {
                dagEnumeration_maxIter = Integer.parseInt(tpaOptions.ENUM_MAX_ITERATION.getDefaultValue());
            }

            if (commandLine.hasOption(tpaOptions.GLOBALHARDMAXIMUMEARLIERDEPARTURE.getOpt())) {
                globalHardMaximumEarlierDeparture = Integer.parseInt(commandLine.getOptionValue(tpaOptions.GLOBALHARDMAXIMUMEARLIERDEPARTURE.getOpt()));
            } else {
                globalHardMaximumEarlierDeparture = Integer.parseInt(tpaOptions.GLOBALHARDMAXIMUMEARLIERDEPARTURE.getDefaultValue());
            }

            if (commandLine.hasOption(tpaOptions.GLOBALHARDMAXIMUMLATERARRIVAL.getOpt())) {
                globalHardMaximumLaterArrival = Integer.parseInt(commandLine.getOptionValue(tpaOptions.GLOBALHARDMAXIMUMLATERARRIVAL.getOpt()));
            } else {
                globalHardMaximumLaterArrival = Integer.parseInt(tpaOptions.GLOBALHARDMAXIMUMLATERARRIVAL.getDefaultValue());
            }

            if (commandLine.hasOption(tpaOptions.GLOBALHARDMINIMUMDWELLTIME.getOpt())) {
                globalHardMinimumDwellTime = Integer.parseInt(commandLine.getOptionValue(tpaOptions.GLOBALHARDMINIMUMDWELLTIME.getOpt()));
            } else {
                globalHardMinimumDwellTime = Integer.parseInt(tpaOptions.GLOBALHARDMINIMUMDWELLTIME.getDefaultValue());
            }


            tpaParser = new TpaParser(applicationProps, fileName);
            capsuleSetupOnly = false;
            return this;
        }

        public int getGlobalHardMaximumEarlierDeparture() {
            return globalHardMaximumEarlierDeparture;
        }

        public int getGlobalHardMinimumDwellTime() {
            return globalHardMinimumDwellTime;
        }

        public int getGlobalHardMaximumLaterArrival() {
            return globalHardMaximumLaterArrival;
        }
    }
}
