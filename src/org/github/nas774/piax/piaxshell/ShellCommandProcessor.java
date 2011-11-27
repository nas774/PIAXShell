package org.github.nas774.piax.piaxshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.piax.agent.AgentHome;
import org.piax.agent.AgentId;
import org.piax.agent.AgentPeer;
import org.piax.agent.NoSuchAgentException;
import org.piax.trans.common.FutureReturn;
import org.piax.trans.common.PeerId;
import org.piax.trans.common.ReturnSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellCommandProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ShellCommandProcessor.class);

    private AgentPeer peer = null;
    private ExecutorService asyncReturnExecutor = Executors.newCachedThreadPool();

    public ShellCommandProcessor(AgentPeer peer) {
        if (peer == null)
            throw new IllegalArgumentException("An argument peer is null.");

        this.peer = peer;
    }

    private void printHelp() {
        System.out
                .print(" *** PIAXShell Help ***\n"
                        + "  i)nfo           show my peer information\n"
                        + "  \n"
                        + "  ag)ents [cat]   list agents have specific category\n"
                        + "  \n"
                        + "  join            join P2P net\n"
                        + "  leave           leave from P2P net\n"
                        + "  \n"
                        + "  mk)agent class name [cat]\n"
                        + "                  create new agent as class and set name and category\n"
                        + "  mk)agent file\n"
                        + "                  create new agent by using file describes agent info\n"
                        + "  dup agent_NO    duplicate agent from which indicated by agent_NO\n"
                        + "  sl)eep agent_NO sleep agent indicated by agent_NO\n"
                        + "  wa)ke agetn_NO  wakeup agent indicated by agent_NO\n"
                        + "  fin agent_NO    destroy agent indicated by agent_NO\n"
                        + "  \n"
                        + "  c)all agent_NO method arg ...\n"
                        + "  c)all aid:agent_ID method arg ...\n"
                        + "  c)all pid:peer_ID aid:agent_ID method arg ...\n"
                        + "                  call remote agent method\n"
                        + "  co,calloneway agent_NO method arg ...\n"
                        + "  co,calloneway aid:agent_ID method arg ...\n"
                        + "  co,calloneway pid:peer_ID aid:agent_ID method arg ...\n"
                        + "                  callOneway remote agent method\n"
                        + "  ca,callasync agent_NO method arg ...\n"
                        + "  ca,callasync aid:agent_ID method arg ...\n"
                        + "  ca,callasync pid:peer_ID aid:agent_ID method arg ...\n"
                        + "                  callAsync remote agent method\n"
                        + "  \n"
                        + "  dc,discover query method arg ...\n"
                        + "                  discoveryCall to agents by query\n"
                        + "  dco,discoveroneway query method arg ...\n"
                        + "                  discoveryCallOneway to agents by query\n"
                        + "  dca,discoverasync query method arg ...\n"
                        + "                  discoveryCallAsync to agents by query\n"
                        + "  dcl,discoverlocation lng lat w h method arg ...\n"
                        + "                  discoveryCall to agents in (lng, lat, w, h) area\n"
                        + "  \n"
                        + "  ?,help          show this help message\n"
                        + "  bye             exit\n" + "\n");
    }

    static Pattern cmd_arg_divide = Pattern.compile("([a-zA-Z][\\w-]*)(\\s.+)?");
    static Pattern arg_parse = Pattern.compile("\\s+(?:(?:\"((?:(?:\\\\\")|[^\"])*)\"?)|([^\"]\\S*))");
    private static String[] split(String line) {
        line = line.trim();
        ArrayList<String> cmd_args = new ArrayList<String>();
        Matcher m = cmd_arg_divide.matcher(line);
        if (!m.matches())
            return null;
        String cmd = m.group(1);
        String arg = m.group(2);

        cmd_args.add(cmd);

        if (arg == null)
            return cmd_args.toArray(new String[cmd_args.size()]);

        m = arg_parse.matcher(arg);
        while (m.find()) {
            for (int i=1 ;i<m.groupCount()+1;i++) {
                if (m.group(i) != null) {
                    cmd_args.add(m.group(i).replaceAll("\\\\", ""));
                    break;
                }
            }
        }
        return cmd_args.toArray(new String[cmd_args.size()]);
    }

    private List<AgentId> agents;

    long stime;

    void mainLoop() {
        agents = new ArrayList<AgentId>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));

        while (true) {
            try {
                System.out.print("Input Command >");
                String input = null;
                // countermeasure for background running with "< /dev/null".
                while (true) {
                    input = reader.readLine();
                    if (input == null) {
                        Thread.sleep(500);
                        continue;
                    }
                    break;
                }
                if (input.equals("")) {
                    continue;
                }
                String[] args = split(input);
                if (args.length == 0) {
                    continue;
                }

                stime = System.currentTimeMillis();

                if (args[0].equals("info") || args[0].equals("i")) {
                    if (args.length != 1) {
                        printHelp();
                        continue;
                    }
                    info();
                } else if (args[0].equals("agents") || args[0].equals("ag")) {
                    if (args.length > 1) {
                        printHelp();
                        continue;
                    }
                    if (args.length == 1) {
                        agents();
                    }
                } else if (args[0].equals("join")) {
                    if (args.length != 1) {
                        printHelp();
                        continue;
                    }
                    join();
                } else if (args[0].equals("leave")) {
                    if (args.length != 1) {
                        printHelp();
                        continue;
                    }
                    leave();
                } else if (args[0].equals("mkagent") || args[0].equals("mk")) {
                    if (args.length < 2 || args.length > 4) {
                        printHelp();
                        continue;
                    }
                    if (args.length == 2) {
                        mkagent(args[1]);
                    } else if (args.length == 3) {
                        mkagent(args[1], args[2]);
                    }
                } else if (args[0].equals("dup")) {
                    if (args.length != 2) {
                        printHelp();
                        continue;
                    }
                    dup(Integer.parseInt(args[1]));
                } else if (args[0].equals("sleep") || args[0].equals("sl")) {
                    if (args.length != 2) {
                        printHelp();
                        continue;
                    }
                    sleep(Integer.parseInt(args[1]));
                } else if (args[0].equals("wake") || args[0].equals("wa")) {
                    if (args.length != 2) {
                        printHelp();
                        continue;
                    }
                    wake(Integer.parseInt(args[1]));
                } else if (args[0].equals("fin")) {
                    if (args.length != 2) {
                        printHelp();
                        continue;
                    }
                    fin(Integer.parseInt(args[1]));
                } else if (args[0].equals("discover") || args[0].equals("dc") ||
                           args[0].equals("discoveroneway") || args[0].equals("dco") ||
                           args[0].equals("discoverasync") || args[0].equals("dca")) {
                    if (args.length < 3) {
                        printHelp();
                        continue;
                    }
                    String query = args[1];
                    String method = args[2];
                    Object[] dcargs = new String[args.length - 3];
                    for (int i = 0; i < dcargs.length; i++) {
                        dcargs[i] = args[i + 3];
                    }
                    if (args[0].equals("discover") || args[0].equals("dc")) {
                        discover(query, method, dcargs);
                    } else if (args[0].equals("discoveroneway") || args[0].equals("dco")) {
                        discoverOneway(query, method, dcargs);
                    } else if (args[0].equals("discoverasync") || args[0].equals("dca")) {
                        discoverAsync(query, method, dcargs);
                    }
                } else if (args[0].equals("discoverlocation") || args[0].equals("dcl")) {
                    if (args.length < 6) {
                        printHelp();
                        continue;
                    }
                    double lng = Double.parseDouble(args[1]);
                    double lat = Double.parseDouble(args[2]);
                    double w = Double.parseDouble(args[3]);
                    double h = Double.parseDouble(args[4]);
                    String method = args[5];
                    Object[] dcargs = new String[args.length - 6];
                    for (int i = 0; i < dcargs.length; i++) {
                        dcargs[i] = args[i + 6];
                    }
                    discoverLocation(lng, lat, w, h, method, dcargs);
                } else if (args[0].equals("call") || args[0].equals("c") ||
                           args[0].equals("calloneway") || args[0].equals("co") ||
                           args[0].equals("callasync") || args[0].equals("ca")) {
                    if (args.length < 3) {
                        printHelp();
                        continue;
                    }
                    int calltype = 0;
                    if (args[0].equals("call") || args[0].equals("c")) {
                        calltype = 0;
                    } else if (args[0].equals("calloneway") || args[0].equals("co")) {
                        calltype = 1;
                    } else if (args[0].equals("callasync") || args[0].equals("ca")) {
                        calltype = 2;
                    }

                    int agno = -1;
                    try {
                        agno = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                    }
                    if (agno != -1) {
                        String method = args[2];
                        Object[] cargs = new String[args.length - 3];
                        for (int i = 0; i < cargs.length; i++) {
                            cargs[i] = args[i + 3];
                        }
                        if (calltype == 0) {
                            call(agno, method, cargs);
                        } else if (calltype == 1) {
                            callOneway(agno, method, cargs);
                        } else if (calltype == 2) {
                            callAsync(agno, method, cargs);
                        }
                    } else if (args[1].toLowerCase().startsWith("aid:")) {
                        AgentId agid = new AgentId(args[1].substring(4));
                        String method = args[2];
                        Object[] cargs = new String[args.length - 3];
                        for (int i = 0; i < cargs.length; i++) {
                            cargs[i] = args[i + 3];
                        }
                        if (calltype == 0) {
                            call(agid, method, cargs);
                        } else if (calltype == 1) {
                            callOneway(agid, method, cargs);
                        } else if (calltype == 2) {
                            callAsync(agid, method, cargs);
                        }
                    } else if (3 < args.length && args[1].toLowerCase().startsWith("pid:") && args[2].toLowerCase().startsWith("aid:")) {
                        PeerId pid = new PeerId(args[1].substring(4));
                        AgentId agid = new AgentId(args[2].substring(4));
                        String method = args[3];
                        Object[] cargs = new String[args.length - 4];
                        for (int i = 0; i < cargs.length; i++) {
                            cargs[i] = args[i + 4];
                        }
                        if (calltype == 0) {
                            call(pid, agid, method, cargs);
                        } else if (calltype == 1) {
                            callOneway(pid, agid, method, cargs);
                        } else if (calltype == 2) {
                            callAsync(pid, agid, method, cargs);
                        }
                    } else {
                        printHelp();
                        continue;
                    }
                } else if (args[0].equals("help") || args[0].equals("?")) {
                    printHelp();
                    continue;
                } else if (args[0].equals("bye")) {

                    if (peer.isOnline()) {
                        leave();
                    }
                    return;
                } else {
                    printHelp();
                    continue;
                }

                long etime = System.currentTimeMillis();
                System.out.println("\t## time (msec): " + (etime - stime));

            } catch (NumberFormatException e) {
                System.out.println("\t>> arg should be number.");
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    void info() {
        try {
            System.out.println(" peerName: " + peer.getPeerName());
            System.out.println(" peerId: " + peer.getHome().getPeerId());
            System.out.println(" locator: " + peer.getIdTransport().getLocator());
            System.out.println(" location: " + peer.getHome().getLocation());
            System.out.println(peer.getOverlayMgr().showTable());
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }

    void agents() {
        try {
            AgentHome home = peer.getHome();
            agents.clear();
            int i = 0;
            for (AgentId agId : home.getAgentIds(null)) {
                agents.add(agId);
                String name = home.getAgentName(agId);
                boolean isSleeping = home.isAgentSleeping(agId);
                System.out.println(" " + i + ". name: " + name + ", ID: "
                        + agId + (isSleeping ? " <sleep>" : ""));
                i++;
            }
        } catch (NoSuchAgentException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    void join() {
        try {
            peer.online();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> join failed.");
        }
    }

    void leave() {
        try {
            peer.offline();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> leave failed.");
        }
    }

    /* agents */
    void mkagent(String clazz, String name) {
        try {
            peer.getHome().createAgent(clazz, name);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot create new agent.");
        }
    }

    void mkagent(String file) {
        String agentPath = System.getProperty("piaxPeer.agent.path");
        File agentFile = new File(new File(agentPath), file);
        if (!agentFile.isFile()) {
            System.out
                    .println("\t>> " + file + " is not found at " + agentPath);
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader(agentFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] items = split(line);
                if (items.length == 2) {
                    peer.getHome().createAgent(items[0], items[1]);
                }
            }
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot create new agent.");
        }
    }

    void dup(int agentNo) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        try {
            peer.getHome().duplicateAgent(agId);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot duplicate agent.");
        }
    }

    void sleep(int agentNo) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        try {
            peer.getHome().sleepAgent(agId);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot sleep agent.");
        }
    }

    void wake(int agentNo) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        try {
            peer.getHome().wakeupAgent(agId);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot wakeup agent.");
        }
    }

    void fin(int agentNo) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        try {
            peer.getHome().destroyAgent(agId);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot destroy agent.");
        }
    }

    void discover(String query, String method, Object... args) {
        ReturnSet<Object> rset = null;
        try {
            rset = peer.getHome().discoveryCallAsync(query, method, args);
        } catch (IllegalStateException e) {
            System.out.println("\t>> not joined.");
            return;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> could not discovery call.");
            return;
        }

        while (rset.hasNext()) {
            Object value;
            try {
                value = rset.getNext(3000);
            } catch (InterruptedException e) {
                System.out.println(" Timeout.");
                break;
            } catch (NoSuchElementException e) {
                break;
            } catch (InvocationTargetException e) {
                long etime = System.currentTimeMillis();
                logger.warn(e.getMessage(), e);
                System.out.println("\t>> cannot call agent.");
                System.out.println(" peerId: " + rset.getThisPeerId());
                AgentId agId = (AgentId) rset.getThisTargetId();
                System.out.println(" agentId: " + agId);
                System.out.println("\t## time (msec): " + (etime - stime));
                continue;
            }
            long etime = System.currentTimeMillis();
            System.out.println(" value: " + value);
            System.out.println(" peerId: " + rset.getThisPeerId());
            AgentId agId = (AgentId) rset.getThisTargetId();
            System.out.println(" agentId: " + agId);
            System.out.println("\t## time (msec): " + (etime - stime));
        }
    }

    void discoverAsync(String query, String method, Object... args) {
        try {
            final ReturnSet<Object> rset = peer.getHome().discoveryCallAsync(query, method, args);
            asyncReturnExecutor.execute(new Runnable() {
                public void run() {
                    while (rset.hasNext()) {
                        Object value;
                        try {
                            value = rset.getNext();
                        } catch (NoSuchElementException e) {
                            break;
                        } catch (InvocationTargetException e) {
                            long etime = System.currentTimeMillis();
                            logger.warn(e.getMessage(), e);
                            System.out.println("\t>> cannot call agent.");
                            System.out.println(" peerId: " + rset.getThisPeerId());
                            AgentId agId = (AgentId) rset.getThisTargetId();
                            System.out.println(" agentId: " + agId);
                            System.out.println("\t## time (msec): " + (etime - stime));
                            continue;
                        }
                        long etime = System.currentTimeMillis();
                        System.out.println(" value: " + value);
                        System.out.println(" peerId: " + rset.getThisPeerId());
                        AgentId agId = (AgentId) rset.getThisTargetId();
                        System.out.println(" agentId: " + agId);
                        System.out.println("\t## time (msec): " + (etime - stime));
                    }
                }
            });
        } catch (IllegalStateException e) {
            System.out.println("\t>> not joined.");
            return;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> could not discovery call.");
            return;
        }
    }

    void discoverOneway(String query, String method, Object... args) {
        peer.getHome().discoveryCallOneway(query, method, args);
    }

    void discoverLocation(double lng, double lat, double w, double h, String method, Object... args) {
        ReturnSet<Object> rset = null;
        try {
            String query = String.format("%s inside rect(%f, %f, %f, %f)", 
                    org.piax.agent.AgentConfigValues.LOCATION_ATTRIB_NAME,
                    lng, lat, w, h);
            rset = peer.getHome().discoveryCallAsync(query, method, args);
        } catch (IllegalStateException e) {
            System.out.println("\t>> not joined.");
            return;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> could not discovery call.");
            return;
        }

        while (rset.hasNext()) {
            Object value;
            try {
                value = rset.getNext(3000);
            } catch (InterruptedException e) {
                System.out.println(" Timeout.");
                break;
            } catch (NoSuchElementException e) {
                break;
            } catch (InvocationTargetException e) {
                long etime = System.currentTimeMillis();
                logger.warn(e.getMessage(), e);
                System.out.println("\t>> cannot call agent.");
                System.out.println(" peerId: " + rset.getThisPeerId());
                AgentId agId = (AgentId) rset.getThisTargetId();
                System.out.println(" agentId: " + agId);
                System.out.println("\t## time (msec): " + (etime - stime));
                continue;
            }
            long etime = System.currentTimeMillis();
            System.out.println(" value: " + value);
            System.out.println(" peerId: " + rset.getThisPeerId());
            AgentId agId = (AgentId) rset.getThisTargetId();
            System.out.println(" agentId: " + agId);
            System.out.println("\t## time (msec): " + (etime - stime));
        }
    }

    void call(int agentNo, String method, Object... cargs) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        call(agId, method, cargs);
    }

    void call(AgentId agid, String method, Object... cargs) {
        call(null, agid, method, cargs);
    }

    void call(PeerId pid, AgentId agid, String method, Object... cargs) {
        try {
            Object obj = null;
            if (pid == null) {
                obj = peer.getHome().call(agid, method, cargs);
            } else {
                obj = peer.getHome().call(pid, agid, method, cargs);
            }
            System.out.println(" return value: " + obj);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot call agent.");
        }
    }

    void callAsync(int agentNo, String method, Object... cargs) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        callAsync(agId, method, cargs);
    }

    void callAsync(AgentId agid, String method, Object... cargs) {
        callAsync(null, agid, method, cargs);
    }

    void callAsync(PeerId pid, AgentId agid, String method, Object... cargs) {
        try {
            final FutureReturn<Object> future;
            if (pid == null) {
                future = peer.getHome().callAsync(agid, method, cargs);
            } else {
                future = peer.getHome().callAsync(pid, agid, method, cargs);
            }
            asyncReturnExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        Object obj = future.get();
                        System.out.println(" return value: " + obj);
                    } catch (Exception e) {
                        logger.warn(e.getMessage(), e);
                        System.out.println("\t>> cannot call agent.");
                    }
                }
            });

        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot call agent.");
        }
    }

    void callOneway(int agentNo, String method, Object... cargs) {
        if (agents.size() <= agentNo) {
            System.out.println("\t>> invalid agent NO.");
            return;
        }
        AgentId agId = agents.get(agentNo);
        callOneway(agId, method, cargs);
    }

    void callOneway(AgentId agid, String method, Object... cargs) {
        callOneway(null, agid, method, cargs);
    }

    void callOneway(PeerId pid, AgentId agid, String method, Object... cargs) {
        try {
            if (pid == null) {
                peer.getHome().callOneway(agid, method, cargs);
            } else {
                peer.getHome().callOneway(pid, agid, method, cargs);
            }
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            System.out.println("\t>> cannot call agent.");
        }
    }}
