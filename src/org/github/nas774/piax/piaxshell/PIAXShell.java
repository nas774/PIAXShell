package org.github.nas774.piax.piaxshell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.piax.agent.AgentHome;
import org.piax.agent.AgentId;
import org.piax.agent.AgentPeer;
import org.piax.agent.NoSuchAgentException;
import org.piax.trans.common.PeerLocator;
import org.piax.trans.ts.tcp.TcpLocator;
import org.piax.trans.util.LocalInetAddrs;
import org.piax.trans.util.MersenneTwister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PIAXShell {
    private static final Logger logger = LoggerFactory
            .getLogger(PIAXShell.class);

    private static final String PROPERTY_FILE = "piaxshell.properties";

    private static final int DEFAULT_PIAX_PORT = 12367;
    private static final String DEFAULT_PEERNAME_PREFIX = "PEER";
    private static final String DEFAULT_AGENT_DIRECTORY = "."; // Default AgentClassFile directory.
    private static final boolean DEFAULT_PEER_AUTOJOIN = false;
    private static final boolean DEFAULT_USE_INTERACTIVESHELL = true;

    private AgentPeer peer = null;

    protected String peerName = "";
    private PeerLocator myLocator = null;
    private Collection<PeerLocator> seeds = null;
    private File[] agclassesdirs = null;

    private boolean autojoin = DEFAULT_PEER_AUTOJOIN;

    private boolean use_interactiveshell = DEFAULT_USE_INTERACTIVESHELL;

    private File agprop = null;

    public static void main(String[] args) {
        PIAXShell shell = new PIAXShell();

        if (!shell.initSettings(args)) {
            printUsage();
            return;
        }
        shell.startShell();
    }

    private static void printUsage() {
        System.out
                .println("Usage: PIAXShell [options] name\n"
                        + "  -j means join automatically after booted.\n"
                        + "  -I means use interactive shell. (default)\n"
                        + "             if add '-' after 'I', disables interactive shell.\n"
                        + "  -e <addr> sets piax address to <addr>\n"
                        + "  -r <port> sets piax port to <spec>\n"
                        + "  -s <seed> sets seed peer address to <seed>\n"
                        + "             <seed> to be host:port form\n"
                        + "  -p <property file> use <property file> instead of default property file\n"
                        + "             if add '-' after 'p', ignores default property file.\n"
                        + "  -a <agent dir> set agent class file directory to <agent dir>\n"
                        + "  -A <agent property> use agent property file or directory instead of default property to <agent property>\n"
                        + "  ex. piaxshell -r 1000 root\n"
                        + "  ex. piaxshell -r 1000 -s 192.168.1.101:2000 foo\n");
    }

    /**
     * Initialize instance by command line argument.
     * 
     * @param args
     *            command line argument.
     * @return true:success false:fail, maybe some errors occured.
     */
    protected boolean initSettings(String args[]) {
        try {
            String propfile = PROPERTY_FILE;

            String tmp_peername = "";
            String tmp_piaxaddress = "";
            String tmp_piaxport = "";
            String tmp_seed = "";
            boolean tmp_autojoin = false;
            boolean tmp_use_interactiveshell = true;
            String tmp_agtdir = "";
            String tmp_agentprops_str = "";

            // Search 'p' option first.
            // If 'p' option is found, read property from given file.
            // If not, read default property file.
            for (int i = 0; i < args.length; i++) {
                if (args[i].charAt(1) == 'p') {
                    if (2 < args[i].length() && '-' == args[i].charAt(2)) {
                        propfile = null;
                    } else {
                        i++;
                        if (i < args.length) {
                            propfile = args[i];
                        } else {
                            logger.error("-p option requires a property filename.");
                            return false;
                        }
                    }
                }
            }

            if (propfile != null) {
                File f = new File(propfile);
                boolean fileexist = f.exists() && f.isFile();

                if (!fileexist && !PROPERTY_FILE.equals(propfile)) {
                    logger.error("The property file you specified does not found. : " + propfile);
                    return false;
                }

                if (fileexist) {
                    if (PROPERTY_FILE.equals(propfile))
                        logger.info("Found default property file." );

                    logger.info("Load from property file. : " + propfile);
                    // Try to read property file.
                    Properties serverprop = new Properties();
                    try {
                        serverprop.load(new FileInputStream(propfile));
                        // may
                        if (serverprop.containsKey("piax.peer.name")) {
                            tmp_peername = serverprop.getProperty("piax.peer.name");
                        }
                        // may
                        if (serverprop.containsKey("piax.peer.address")) {
                            tmp_piaxaddress = serverprop.getProperty("piax.peer.address");
                        }
                        // may
                        if (serverprop.containsKey("piax.peer.port")) {
                            tmp_piaxport = serverprop.getProperty("piax.peer.port");
                        }
                        // may
                        if (serverprop.containsKey("piax.peer.seed")) {
                            tmp_seed = serverprop.getProperty("piax.peer.seed");
                        }
                        // may
                        if (serverprop.containsKey("piax.peer.autojoin")) {
                            String tmp_autojoin_str = serverprop
                                    .getProperty("piax.peer.autojoin");
                            if (tmp_autojoin_str != null
                                    && !tmp_autojoin_str.equals("")
                                    && !tmp_autojoin_str.equals("0"))
                                tmp_autojoin = true;
                        }
                        // may
                        if (serverprop.containsKey("piax.shell.useinteractive")) {
                            String tmp_use_interactiveshell_str = serverprop
                                    .getProperty("piax.shell.useinteractive");
                            tmp_use_interactiveshell_str = tmp_use_interactiveshell_str.trim();
                            if (tmp_use_interactiveshell_str != null && 
                                !tmp_use_interactiveshell_str.equals("")) {
                                if (tmp_use_interactiveshell_str.equals("0"))
                                    tmp_use_interactiveshell = false;
                            }
                        }
                        // may
                        if (serverprop.containsKey("piax.agent.directory")) {
                            tmp_agtdir = serverprop.getProperty("piax.agent.directory");
                        }
                        // may
                        if (serverprop.containsKey("piax.agentprops")) {
                            tmp_agentprops_str = serverprop
                                    .getProperty("piax.agentprops");
                        }
                    } catch (FileNotFoundException e) {
                        logger.warn("Property file not found. : " + propfile);
                    } catch (IOException e) {
                        logger.warn("IO error at reading property file. : " + propfile);
                    }
                }
            }

            boolean isFault = false;

            // Read setting from command line options.
            // Configurations in property file are overwritten by command-line options.
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].trim();
                if (args[i].startsWith("-")) {
                    switch (arg.charAt(1)) {
                    case 'e':
                        i++;
                        if (i < args.length) {
                            tmp_piaxaddress = args[i];
                        }
                        break;
                    case 'r':
                        i++;
                        if (i < args.length) {
                            tmp_piaxport = args[i];
                        }
                        break;
                    case 'j': {
                        tmp_autojoin = true;
                        String tmp_autojoin_str = arg.substring(2);
                        if (tmp_autojoin_str.equals("-"))
                            tmp_autojoin = false;
                        break;
                    }
                    case 'I': {
                        tmp_use_interactiveshell = true;
                        String tmp_use_interactiveshell_str = arg.substring(2);
                        if (tmp_use_interactiveshell_str.equals("-"))
                            tmp_use_interactiveshell = false;
                        break;
                    }
                    case 's':
                        i++;
                        if (i < args.length) {
                            tmp_seed = args[i];
                        } else {
                            logger.error("-s option requires a seed peer's address.");
                        }
                        break;
                    case 'p':
                        if (2 < args[i].length() && '-' == args[i].charAt(2)) {
                        } else {
                            i++;
                        }
                        // already proced.
                        break;
                    case 'A':
                        i++;
                        if (i < args.length) {
                            tmp_agentprops_str = args[i];
                        } else {
                            logger.error("-A option requires a agent property filename or directory.");
                        }
                        break;
                    case 'a':
                        i++;
                        if (i < args.length) {
                            tmp_agtdir = args[i];
                        } else {
                            logger.error("-a option requires a agent classfile directorty.");
                        }
                        break;
                    case '?':
                    case 'h':
                        return false;
                    default:
                        logger.error("Found an undefined option : " + args[i]);
                        return false;
                    }
                } else {
                    tmp_peername = arg;
                }
            }

            // Setup instance fields.
            if (tmp_peername.equals("")) {
                logger.warn("Peer name is not specified. Set generated name.");
                tmp_peername = DEFAULT_PEERNAME_PREFIX
                        + new MersenneTwister().nextInt(10 * 6);
            }
            peerName = tmp_peername;
            logger.info("Peer name : " + peerName);

            if (tmp_agtdir.equals("")) {
                logger.warn("Agent class file directory is not specified. Set default.");
                tmp_agtdir = DEFAULT_AGENT_DIRECTORY;
            }
            File agtdir = new File(tmp_agtdir);
            if (agtdir.exists()) {
                logger.info("Agent class file directory : " + tmp_agtdir);
                agclassesdirs = new File[] { agtdir };
            } else {
                logger.error("Agent class file directory you specified isn't found. : "
                                + tmp_agtdir);
                isFault = true;
            }

            autojoin = tmp_autojoin;
            logger.info("Auto join : " + autojoin);

            use_interactiveshell = tmp_use_interactiveshell;
            logger.info("Use Interactive Shell : " + use_interactiveshell);

            agprop = null;
            if (tmp_agentprops_str != null && !tmp_agentprops_str.equals("")) {
                File tmp_agprop = new File(tmp_agentprops_str);
                if (!tmp_agprop.exists()) {
                    logger.warn("Agent property file or directory can not be found. : "
                                    + tmp_agentprops_str);
                } else {
                    agprop = tmp_agprop;
                }
            }
            if (agprop == null) {
                logger.info("Agent property file or directory : <not use>");
            } else {
                if (agprop.isFile()) {
                    logger.info("Agent property file : "
                            + agprop.getAbsolutePath());
                } else if (agprop.isDirectory()) {
                    logger.info("Agent property directory : "
                            + agprop.getAbsolutePath());
                }
            }

            if (tmp_piaxaddress.equals("")) {
                logger.warn("A PIAX address is not specified. Choose appropriate address.");
                tmp_piaxaddress = LocalInetAddrs.choice().getHostAddress();
            }
            if (tmp_piaxport.equals("")) {
                logger.warn("A PIAX port is not specified. Set default.");
                tmp_piaxport = Integer.toString(DEFAULT_PIAX_PORT);
            }

            logger.info("PIAX address : " + tmp_piaxaddress);
            logger.info("PIAX port : " + tmp_piaxport);
            myLocator = new TcpLocator(new InetSocketAddress(tmp_piaxaddress, Integer.parseInt(tmp_piaxport)));
            if (!tmp_seed.equals("")) {
                String[] seedEle = tmp_seed.split(":");
                seeds = Collections.singleton((PeerLocator) new TcpLocator(
                        new InetSocketAddress(seedEle[0], Integer
                                .parseInt(seedEle[1]))));
            } else {
                logger.info("Seed peers are not specified. Run as a seed peer.");
                seeds = Collections.singleton(myLocator);
            }

            return !isFault;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    public void startShell() {
        // - setup phase

        // Initialize PIAX
        try {
            peer = new AgentPeer(null, peerName, myLocator, seeds, false,
                    agclassesdirs);
        } catch (IOException e) {
            logger.error("*** PIAX not started as IO Error.", e);
            return;
        } catch (IllegalArgumentException e) {
            logger.error("*** PIAX not started as Argument Error.", e);
            return;
        }

        // Create agents from agent property files.
        if (agprop != null) {
            if (agprop.isDirectory()) {
                for (File file : agprop.listFiles()) {
                    createAgentFromPropertyFile(file);
                }
            } else {
                createAgentFromPropertyFile(agprop);
            }
        }

        try {
            // Auto join
            if (autojoin) {
                peer.online();
                logger.info("PIAX joined.");
            }
    
            // Activate agents.
            notifyActivate();

            logger.info("Finished initializing");
            if (use_interactiveshell) {
                // Run console interactive shell.
                logger.info("Start interactive shell.");
                new ShellCommandProcessor(peer).mainLoop();
            } else {
                try {
                    while (true) {
                        Thread.sleep(5 * 60 * 1000);
                    }
                } catch (InterruptedException e) {
                }
            }

            // - Terminate phase

            // Dispose all agents.
            Set<AgentId> ags = new HashSet<AgentId>();
            for (AgentId agId : peer.getHome().getAgentIds()) {
                ags.add(agId);
            }
            for (AgentId agId : ags) {
                try {
                    peer.getHome().destroyAgent(agId);
                } catch (NoSuchAgentException e) {
                    logger.debug("Ignore a NoSuchAgentException.");
                }
            }

            // Terminate PIAX
            if (peer.isOnline())
                peer.offline();     // Leave from PIAX network.
        } catch (Exception e1) {
            logger.error(e1.getMessage(), e1);
        }
        peer.fin(); // Finalize PIAX.
    }

    /**
     * Create agents from agent property files.
     * 
     * @param file
     *            A directory which has agent property.
     */
    private void createAgentFromPropertyFile(File file) {
        logger.info("Opening property file : " + file.getName());
        Properties agentprop = new Properties();

        try {
            agentprop.load(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            logger.error("Agent property file not found.");
            // Ignore unreadable file.
            return;
        } catch (IOException e) {
            logger.error("IO error when loading agent property file.");
            // Ignore unreadable file.
            return;
        }

        String classname = agentprop.getProperty("piax.agent.class");
        String agentname = agentprop.getProperty("piax.agent.name");

        // Check null or empty element.
        if (classname == null || classname.equals("")) {
            logger.error("piax.agent.class is null : " + classname);
            return;
        }
        if (agentname == null || agentname.equals("")) {
            logger.error("piax.agent.name is null : " + agentname);
            return;
        }

        try {
            AgentHome home = peer.getHome();
            AgentId agtid = home.createAgent(classname, agentname);
            if (!(Boolean) home.call(agtid, "initAgent", new Object[] { file })) {
                logger.error("Failed initializing Agent. Class:" + classname
                        + " Name:" + agentname);
                // Dispose if creating agent failed.
                home.destroyAgent(agtid);
                return;
            }
            logger.info("Craeted an agent named " + agentname + " based by " + classname + " as ID:" + agtid);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void notifyActivate() {
        AgentHome home = peer.getHome();
        for (AgentId aid : home.getAgentIds()) {
            home.callOneway(aid, "activate", new Object[]{});
        }
    }
}
