/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2008 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 * 
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.server.sreceiver.sysmon;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.server.Packet;
import tigase.server.sreceiver.PropertyItem;
import tigase.server.sreceiver.RepoRosterTask;
import tigase.stats.StatisticsList;
import tigase.util.ClassUtil;
import tigase.xmpp.StanzaType;

import static tigase.server.sreceiver.PropertyConstants.*;
import static tigase.server.sreceiver.sysmon.ResourceMonitorIfc.*;

/**
 * Created: Dec 6, 2008 8:12:41 PM
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class SystemMonitorTask extends RepoRosterTask {

  private static Logger log =
					Logger.getLogger("tigase.server.sreceiver.sysmon.SystemMonitorTask");

	private static final String TASK_TYPE = "System Monitor";
	private static final String TASK_HELP =
		"This is a system monitor task." +
		" It monitors system resources usage and sends notifications" +
		" to subscribed users. It allos responds to your messages with" +
		" a simple reply message. This is to ensure the monitor works.";
	private static final String MONITORS_CLASSES_PROP_KEY =
					"Monitor implementations";
	private static final String WARNING_THRESHOLD_PROP_KEY =
					"Warning threshold";
	//private long interval = 10*SECOND;

	private String[] all_monitors = null;
	private String[] selected_monitors = null;
	private Map<String, ResourceMonitorIfc> monitors =
					new LinkedHashMap<String, ResourceMonitorIfc>();
	private float warning_threshold = 0.8f;

	private enum command {
		help(" - Displays help info."),
		state(" - Displays current state from all monitors."),
		threshold(" [0.NN] - sets/displays current threshold value.");

		private String helpText = null;

		private command(String helpText) {
			this.helpText = helpText;
		}

		public String getHelp() {
			return helpText;
		}

	};

	private Timer tasks = null;

	public SystemMonitorTask() {
		try {
			Set<ResourceMonitorIfc> mons =
							ClassUtil.getImplementations(ResourceMonitorIfc.class);
			all_monitors = new String[mons.size()];
			int idx = 0;
			for (ResourceMonitorIfc monitor : mons) {
				all_monitors[idx++] = monitor.getClass().getName();
			}
		} catch (Exception ex) {
			log.log(Level.SEVERE, "Can't load resource monitors implementations", ex);
			all_monitors = new String[2];
			all_monitors[0] = "tigase.server.sreceiver.sysmon.CPUMonitor";
			all_monitors[1] = "tigase.server.sreceiver.sysmon.MemMonitor";
		}
	}

	protected void sendPacketsOut(Queue<Packet> input) {
		Queue<Packet> results = new LinkedList<Packet>();
		for (Packet packet : input) {
			if (packet.getElemName() == "message" || packet.getElemTo() == null ||
							packet.getElemTo().isEmpty()) {
				super.processMessage(packet, results);
			} else {
				results.add(packet);
			}
		}
		for (Packet packet : results) {
			addOutPacket(packet);
		}
	}

	protected void sendPacketOut(Packet input) {
		Queue<Packet> results = new LinkedList<Packet>();
		if (input.getElemName() == "message" || input.getElemTo() == null ||
						input.getElemTo().isEmpty()) {
			super.processMessage(input, results);
		} else {
			results.add(input);
		}
		for (Packet packet : results) {
			addOutPacket(packet);
		}
	}

	private void monitor10Secs() {
		Queue<Packet> results = new LinkedList<Packet>();
		for (ResourceMonitorIfc monitor : monitors.values()) {
			monitor.check10Secs(results);
		}
		sendPacketsOut(results);
	}
	
	private void monitor1Min() {
		Queue<Packet> results = new LinkedList<Packet>();
		for (ResourceMonitorIfc monitor : monitors.values()) {
			monitor.check1Min(results);
		}
		sendPacketsOut(results);
	}
	
	private void monitor1Hour() {
		Queue<Packet> results = new LinkedList<Packet>();
		for (ResourceMonitorIfc monitor : monitors.values()) {
			monitor.check1Hour(results);
		}
		sendPacketsOut(results);
	}
	
	private void monitor1Day() {
		Queue<Packet> results = new LinkedList<Packet>();
		for (ResourceMonitorIfc monitor : monitors.values()) {
			monitor.check1Day(results);
		}
		sendPacketsOut(results);
	}

	@Override
	public void init(Queue<Packet> results) {
		super.init(results);
		tasks = new Timer("SystemMonitorTask", true);
		tasks.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitor10Secs();
			}
		}, INTERVAL_10SECS, INTERVAL_10SECS);
		tasks.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitor1Min();
			}
		}, INTERVAL_1MIN, INTERVAL_1MIN);
		tasks.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitor1Hour();
			}
		}, INTERVAL_1HOUR, INTERVAL_1HOUR);
		tasks.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitor1Day();
			}
		}, INTERVAL_1DAY, INTERVAL_1DAY);
	}

	@Override
	public void destroy(Queue<Packet> results) {
		tasks.cancel();
		tasks = null;
		super.destroy(results);
	}

	@Override
	public String getType() {
		return TASK_TYPE;
	}

	@Override
	public String getHelp() {
		return TASK_HELP;
	}

	private String commandsHelp() {
		StringBuilder sb = new StringBuilder();
		for (command comm : command.values()) {
			sb.append("//" + comm.name() + comm.getHelp() + "\n");
		}
		for (ResourceMonitorIfc monitor : monitors.values()) {
			sb.append(monitor.commandsHelp());
		}
		return "Available commands are:\n" + sb.toString();
	}

	private boolean isPostCommand(Packet packet) {
		String body = packet.getElemCData("/message/body");
		if (body != null) {
			for (command comm: command.values()) {
				if (body.startsWith("//" + comm.toString())) {
					return true;
				}
			}
		}
		return false;
	}

	private ResourceMonitorIfc monitorForCommand(Packet packet) {
		String body = packet.getElemCData("/message/body");
		if (body != null) {
			for (ResourceMonitorIfc monitor : monitors.values()) {
				if (monitor.isMonitorCommand(body)) {
					return monitor;
				}
			}
		}
		return null;
	}

	@Override
	public void setParams(Map<String, Object> map)	{
		super.setParams(map);
		String threshold = (String) map.get(WARNING_THRESHOLD_PROP_KEY);
		if (threshold != null) {
			// In fact it can be null if this is just a configuration change
			// in this case only changed properties are passed to the task
			try {
				float tresh = Float.parseFloat(threshold);
				warning_threshold = tresh;
			} catch (Exception e) {
				log.warning("Incorrect warning threshold, using default" + threshold);
			}
		}
		String[] mons = null;
		try {
			mons = (String[]) map.get(MONITORS_CLASSES_PROP_KEY);
		} catch (Exception e) {
			log.warning("Incorrect monitors list: " + 
							map.get(MONITORS_CLASSES_PROP_KEY));
			mons = all_monitors;
		}
		if (mons != null) {
			selected_monitors = mons;
			for (ResourceMonitorIfc monitor : monitors.values()) {
				monitor.destroy();
			}
			monitors.clear();
			for (String string : mons) {
				try {
					ResourceMonitorIfc resMon =
									(ResourceMonitorIfc) Class.forName(string).newInstance();
					String monJid = getJID() + "/" + resMon.getClass().getSimpleName();
					resMon.init(monJid, warning_threshold, this);
					monitors.put(monJid, resMon);
					log.config("Loaded resource monitor: " + monJid);
				} catch (Exception ex) {
					log.log(Level.SEVERE,
									"Can't instantiate resource monitor: " + string, ex);
				}
			}
		}
	}

	@Override
	public Map<String, PropertyItem> getParams() {
		Map<String, PropertyItem> props = super.getParams();
		props.put(MONITORS_CLASSES_PROP_KEY,
						new PropertyItem(MONITORS_CLASSES_PROP_KEY,
						MONITORS_CLASSES_PROP_KEY, selected_monitors, all_monitors,
						"List of system monitors available for use"));
		props.put(WARNING_THRESHOLD_PROP_KEY,
						new PropertyItem(WARNING_THRESHOLD_PROP_KEY,
						WARNING_THRESHOLD_PROP_KEY, warning_threshold));
//	log.fine("selected_monitors: " + Arrays.toString(selected_monitors) +
//						", all_monitors: " + Arrays.toString(all_monitors));
		return props;
	}

	@Override
	public Map<String, PropertyItem> getDefaultParams() {
		Map<String, PropertyItem> defs = super.getDefaultParams();
		defs.put(DESCRIPTION_PROP_KEY, new PropertyItem(DESCRIPTION_PROP_KEY,
						DESCRIPTION_DISPL_NAME, "System Monitor Task"));
		defs.put(MESSAGE_TYPE_PROP_KEY,
						new PropertyItem(MESSAGE_TYPE_PROP_KEY,
						MESSAGE_TYPE_DISPL_NAME, MessageType.NORMAL));
		defs.put(ONLINE_ONLY_PROP_KEY,
						new PropertyItem(ONLINE_ONLY_PROP_KEY,
						ONLINE_ONLY_DISPL_NAME, false));
		defs.put(REPLACE_SENDER_PROP_KEY,
						new PropertyItem(REPLACE_SENDER_PROP_KEY,
						REPLACE_SENDER_DISPL_NAME, SenderAddress.REPLACE_SRECV));
		defs.put(SUBSCR_RESTRICTIONS_PROP_KEY,
						new PropertyItem(SUBSCR_RESTRICTIONS_PROP_KEY,
						SUBSCR_RESTRICTIONS_DISPL_NAME, SubscrRestrictions.MODERATED));
		defs.put(MONITORS_CLASSES_PROP_KEY,
						new PropertyItem(MONITORS_CLASSES_PROP_KEY,
						MONITORS_CLASSES_PROP_KEY, all_monitors, all_monitors,
						"List of system monitors available for use"));
		defs.put(WARNING_THRESHOLD_PROP_KEY,
						new PropertyItem(WARNING_THRESHOLD_PROP_KEY,
						WARNING_THRESHOLD_PROP_KEY, warning_threshold));
		return defs;
	}

	private void runCommand(Packet packet, Queue<Packet> results) {
		String body = packet.getElemCData("/message/body");
		String[] body_split = body.split("\\s");
		command comm = command.valueOf(body_split[0].substring(2));
		switch (comm) {
			case help:
				results.offer(Packet.getMessage(packet.getElemFrom(),
								packet.getElemTo(), StanzaType.chat, commandsHelp(),
								"Commands description", null));
				break;
			case state:
				StringBuilder sb = new StringBuilder("\n");
				for (ResourceMonitorIfc resmon : monitors.values()) {
					sb.append(resmon.getClass().getSimpleName() + ":\n");
					sb.append(resmon.getState() + "\n");
				}
				results.offer(Packet.getMessage(packet.getElemFrom(),
								packet.getElemTo(), StanzaType.chat, sb.toString(),
								"Monitors State", null));
				break;
			case threshold:
				if (body_split.length > 1) {
					boolean correct = false;
					try {
						float newthreshold = Float.parseFloat(body_split[1]);
						if (newthreshold > 0 && newthreshold < 1) {
							warning_threshold = newthreshold;
							for (Map.Entry<String, ResourceMonitorIfc> resmon : monitors.entrySet()) {
								resmon.getValue().init(resmon.getKey(), warning_threshold, this);
							}
							correct = true;
						}
					} catch (Exception e) { }
					if (correct) {
						results.offer(Packet.getMessage(packet.getElemFrom(),
										packet.getElemTo(), StanzaType.chat,
										"New threshold set to: " + warning_threshold + "\n",
										"Threshold command.", null));
					} else {
						results.offer(Packet.getMessage(packet.getElemFrom(),
										packet.getElemTo(), StanzaType.chat,
										"Incorrect threshold givenm using the old threshold: " +
										warning_threshold + "\n" +
										"Correct threshold is a float point number 0 < N < 1.",
										"Threshold command.", null));
					}
				} else {
					results.offer(Packet.getMessage(packet.getElemFrom(),
									packet.getElemTo(), StanzaType.chat,
									"Current threshold value is: " + warning_threshold,
									"Threshold command.", null));
				}
				break;
		}
	}

	private void runMonitorCommand(ResourceMonitorIfc monitor, 
					Packet packet, Queue<Packet> results) {
		String body = packet.getElemCData("/message/body");
		String[] body_split = body.split("\\s");
		String result = monitor.runCommand(body_split);
		if (result == null) {
			result = "Monitor " + monitor.getClass().getSimpleName() +
							" command was run but returned no results.";
		}
		results.offer(Packet.getMessage(packet.getElemFrom(),
						packet.getElemTo(), StanzaType.chat, result,
						monitor.getClass().getSimpleName() + " command.", null));
	}

	@Override
	protected void processMessage(Packet packet, Queue<Packet> results) {
		if (isPostCommand(packet)) {
			runCommand(packet, results);
		} else {
			ResourceMonitorIfc monitor = monitorForCommand(packet);
			if (monitor != null) {
				runMonitorCommand(monitor, packet, results);
			} else {
				String body = packet.getElemCData("/message/body");
				results.offer(Packet.getMessage(packet.getElemFrom(),
								packet.getElemTo(), StanzaType.normal,
								"This is response to your message: [" + body + "]",
								"Response", null));
			}
		}
	}

	@Override
	public void getStatistics(StatisticsList list) {
    super.getStatistics(list);
		for (ResourceMonitorIfc monitor : monitors.values()) {
			monitor.getStatistics(list);
		}
	}

}
