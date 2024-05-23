package org.sbone.research;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sbone.research.MainKt.getOptions;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

public class CliParserTest {

    @Test
    public void testConfigPathOption() throws ParseException {
        String[] args = {"--configPath", "/path/to/config"};
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("configPath"));
        assertEquals("/path/to/config", cmd.getOptionValue("configPath"));
    }

    @Test
    public void testBaseDirOption() throws ParseException {
        String[] args = {"--baseDir", "/path/to/base"};
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("baseDir"));
        assertEquals("/path/to/base", cmd.getOptionValue("baseDir"));
    }

    @Test
    public void testJarsOption() throws ParseException {
        String[] args = {"--jars", "jar1.jar", "jar2.jar"};
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("jars"));
        String[] jars = cmd.getOptionValues("jars");
        assertArrayEquals(new String[]{"jar1.jar", "jar2.jar"}, jars);
    }

    @Test
    public void testRunsNumberOption() throws ParseException {
        String[] args = {"--runsNumber", "10"};
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("runsNumber"));
        assertEquals("10", cmd.getOptionValue("runsNumber"));
    }

    @Test
    public void testTimeoutOption() throws ParseException {
        String[] args = {"--timeout", "300"};
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("timeout"));
        assertEquals("300", cmd.getOptionValue("timeout"));
    }

    @Test
    public void testAllOptionsTogether() throws ParseException {
        String[] args = {
            "--configPath", "/path/to/config",
            "--baseDir", "/path/to/base",
            "--jars", "jar1.jar", "jar2.jar",
            "--runsNumber", "10",
            "--timeout", "300"
        };
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        assertTrue(cmd.hasOption("configPath"));
        assertEquals("/path/to/config", cmd.getOptionValue("configPath"));

        assertTrue(cmd.hasOption("baseDir"));
        assertEquals("/path/to/base", cmd.getOptionValue("baseDir"));

        assertTrue(cmd.hasOption("jars"));
        String[] jars = cmd.getOptionValues("jars");
        assertArrayEquals(new String[]{"jar1.jar", "jar2.jar"}, jars);

        assertTrue(cmd.hasOption("runsNumber"));
        assertEquals("10", cmd.getOptionValue("runsNumber"));

        assertTrue(cmd.hasOption("timeout"));
        assertEquals("300", cmd.getOptionValue("timeout"));
    }
}