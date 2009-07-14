/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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

package tigase.stats;

import java.util.List;
import javax.management.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import tigase.server.QueueType;
import tigase.sys.TigaseRuntime;

/**
 * Class StatisticsProvider
 *
 * @author kobit
 */
public class StatisticsProvider extends StandardMBean
				implements StatisticsProviderMBean {

	private StatisticsCollector theRef;

	public StatisticsProvider(StatisticsCollector theRef)
					throws NotCompliantMBeanException {
		//WARNING Uncomment the following call to super() to make this class
		//compile (see BUG ID 122377)
		super(StatisticsProviderMBean.class, false);
		this.theRef = theRef;
	}
	
	@Override
	public MBeanInfo getMBeanInfo() {
		MBeanInfo mbinfo = super.getMBeanInfo();
		return new MBeanInfo(mbinfo.getClassName(),
						mbinfo.getDescription(),
						mbinfo.getAttributes(),
						mbinfo.getConstructors(),
						mbinfo.getOperations(),
						getNotificationInfo());
	}
	
	public MBeanNotificationInfo[] getNotificationInfo() {
		return new MBeanNotificationInfo[] {};
	}

	/**
	 * Override customization hook:
	 * You can supply a customized description for MBeanInfo.getDescription()
	 *
	 * @param info
	 * @return
	 */
	@Override
	protected String getDescription(MBeanInfo info) {
		return "Provides the Tigase server statistics";
	}

	/**
	 * Override customization hook:
	 * You can supply a customized description for MBeanAttributeInfo.getDescription()
	 *
	 * @param info
	 * @return
	 */
	@Override
	protected String getDescription(MBeanAttributeInfo info) {
		String description = null;
		if (info.getName().equals("AllStats")) {
			description = "Collection of statistics from all components.";
		} else if (info.getName().equals("ComponentsNames")) {
			description = "List of components names for which statistics are available";
		} else if (info.getName().equals("Name")) {
			description = "This is a component name - name of the statistics collector component,";
		} else if (info.getName().equals("getUptime")) {
			description = "Returns JVM uptime.";
		} else if (info.getName().equals("getProcesCPUTime")) {
			description = "Returns JMV process CPU time.";
		}
		return description;
	}

	/**
	 * Override customization hook:
	 * You can supply a customized description for MBeanParameterInfo.getDescription()
	 *
	 * @param op
	 * @param param
	 * @param sequence
	 * @return
	 */
	@Override
	protected String getDescription(MBeanOperationInfo op,
					MBeanParameterInfo param, int sequence) {
		if (op.getName().equals("getAllStats")) {
			switch (sequence) {
				case 0:
					return "Statistics level, 0 - All, 500 - Medium, 800 - Minimal";
				default:
					return null;
			}
		} else if (op.getName().equals("getComponentStats")) {
			switch (sequence) {
				case 0:
					return "The component name to provide statistics for";
				case 1:
					return "Statistics level, 0 - All, 500 - Medium, 800 - Minimal";
				default:
					return null;
			}
		}
		return null;
	}

	/**
	 * Override customization hook:
	 * You can supply a customized description for MBeanParameterInfo.getName()
	 *
	 * @param op
	 * @param param
	 * @param sequence
	 * @return
	 */
	@Override
	protected String getParameterName(MBeanOperationInfo op,
					MBeanParameterInfo param, int sequence) {
		if (op.getName().equals("getAllStats")) {
			switch (sequence) {
				case 0:
					return "level";
				default:
					return null;
			}
		} else if (op.getName().equals("getComponentStats")) {
			switch (sequence) {
				case 0:
					return "compName";
				case 1:
					return "level";
				default:
					return null;
			}
		}
		return null;
	}

	/**
	 * Override customization hook:
	 * You can supply a customized description for MBeanOperationInfo.getDescription()
	 *
	 * @param info
	 * @return
	 */
	@Override
	protected String getDescription(MBeanOperationInfo info) {
		String description = null;
		MBeanParameterInfo[] params = info.getSignature();
		String[] signature = new String[params.length];
		for (int i = 0; i < params.length;
						i++) {
			signature[i] = params[i].getType();
		}
		String[] methodSignature;
		methodSignature = new String[]{java.lang.Integer.TYPE.getName()};
		if (info.getName().equals("getAllStats") &&
						Arrays.equals(signature, methodSignature)) {
			description = "Provides statistics for all components for a given level.";
		}
		methodSignature =	new String[]{java.lang.String.class.getName(),
						java.lang.Integer.TYPE.getName()};
		if (info.getName().equals("getComponentStats") &&
						Arrays.equals(signature, methodSignature)) {
			description = "Provides statistics for a given component name and statistics level.";
		}
		return description;
	}

//	/**
//	 * Get Attribute exposed for management
//	 * @return java.util.Map<String, String>
//	 */
//	@Override
//	public Map getAllStats() {
//		return getAllStats(0);
//	}

	/**
	 * Get Attribute exposed for management
	 */
	@Override
	public List getComponentsNames() {
		return theRef.getComponentsNames();
	}

	/**
	 * Get Attribute exposed for management
	 */
	@Override
	public String getName() {
		return theRef.getName();
	}

	private Map<String, String> getMapFromList(StatisticsList list) {
		if (list != null) {
			Map<String, String> result = new LinkedHashMap<String, String>();
			for (StatRecord rec : list) {
				String key = rec.getComponent() + "/" + rec.getDescription();
				String value = rec.getValue();
				if (rec.getType() == StatisticType.LIST) {
				value = rec.getListValue().toString();
				}
				result.put(key, value);
			}
			return result;
		} else {
			return null;
		}
	}

	/**
	 * Operation exposed for management
	 * @param param0 Statistics level, 0 - All, 500 - Medium, 800 - Minimal
	 * @return java.util.Map<String, String>
	 */
	@Override
	public Map<String, String> getAllStats(int level) {
		StatisticsList list = new StatisticsList(Level.parse(""+level));
		theRef.getAllStats(list);
		return getMapFromList(list);
	}

	/**
	 * Operation exposed for management
	 * @param param0 The component name to provide statistics for
	 * @param param1 Statistics level, 0 - All, 500 - Medium, 800 - Minimal
	 * @return java.util.Map<String, String>
	 */
	@Override
	public Map<String, String> getComponentStats(String compName, int level) {
		StatisticsList list = new StatisticsList(Level.parse(""+level));
		theRef.getComponentStats(compName, list);
		return getMapFromList(list);
	}

	@Override
	public float getCPUUsage() {
		return TigaseRuntime.getTigaseRuntime().getCPUUsage();
	}

	@Override
	public float getHeapMemUsage() {
		return TigaseRuntime.getTigaseRuntime().getHeapMemUsage();
	}

	@Override
  public int getCPUsNumber() {
		return TigaseRuntime.getTigaseRuntime().getCPUsNumber();
	}

	@Override
	public long getUptime() {
		return TigaseRuntime.getTigaseRuntime().getUptime();
	}

	@Override
	public long getProcesCPUTime() {
	  return TigaseRuntime.getTigaseRuntime().getProcessCPUTime();
	}

	@Override
	public int getConnectionsNumber() {
		cache.updateIfOlder(1000);
		return cache.clientConnections;
	}

	@Override
	public int getClusterCacheSize() {
		cache.updateIfOlder(1000);
		return cache.clusterCache;
	}

	@Override
	public int getQueueSize() {
		cache.updateIfOlder(1000);
		return cache.queueSize;
	}

	@Override
	public long getQueueOverflow() {
		cache.updateIfOlder(1000);
		return cache.queueOverflow;
	}

	@Override
	public long getPacketsNumber() {
		cache.updateIfOlder(1000);
		return cache.smPackets;
	}

	@Override
	public long getClusterPackets() {
		cache.updateIfOlder(1000);
		return cache.clusterPackets;
	}

	@Override
	public String getSystemDetails() {
		cache.updateIfOlder(1000);
		return cache.systemDetails;
	}

	@Override
	public long getMessagesNumber() {
		cache.updateIfOlder(1000);
		return cache.messagesNumber;
	}

	@Override
	public long getPresencesNumber() {
		cache.updateIfOlder(1000);
		return cache.presencesNumber;
	}

	@Override
	public long getIQOtherNumber() {
		cache.updateIfOlder(1000);
		return cache.iqOtherNumber;
	}

	@Override
	public long getIQAuthNumber() {
		cache.updateIfOlder(1000);
		return cache.iqAuthNumber;
	}


	private StatisticsCache cache = new StatisticsCache();

	private class StatisticsCache {

		private static final String CL_COMP = "cl-comp";
		private static final String SM_COMP = "sess-man";
		private static final String C2S_COMP = "c2s";
		private static final String BOSH_COMP = "bosh";

		private long lastUpdate = 0;
		private StatisticsList allStats = new StatisticsList(Level.ALL);
		private long clusterPackets = 0;
		private long smPackets = 0;
		private int clientConnections = 0;
		private int clusterCache = 0;
		private int queueSize = 0;
		private long queueOverflow = 0;
		private long messagesNumber = 0;
		private long presencesNumber = 0;
		private long iqOtherNumber = 0;
		private long iqAuthNumber = 0;
		private String systemDetails = "";

		private synchronized void updateIfOlder(long time) {
		  if (System.currentTimeMillis() - lastUpdate > time) {
				update();
				updateSystemDetails();
			}
		}

		private void update() {
			allStats = new StatisticsList(Level.FINER);
			theRef.getAllStats(allStats);
			//System.out.println(allStats.toString());
			clusterPackets = allStats.getValue(CL_COMP,
							StatisticType.MSG_RECEIVED_OK.getDescription(), 0L) +
							allStats.getValue(CL_COMP,
							StatisticType.MSG_SENT_OK.getDescription(), 0L);
			smPackets = allStats.getValue(SM_COMP,
							StatisticType.MSG_RECEIVED_OK.getDescription(), 0L) +
							allStats.getValue(SM_COMP,
							StatisticType.MSG_SENT_OK.getDescription(), 0L);
			clientConnections = allStats.getValue(C2S_COMP,
							"Open connections", 0) +
							allStats.getValue(BOSH_COMP,
							"Open connections", 0);
			clusterCache = allStats.getValue("cl-caching-strat", "Cached JIDs", 0);
			messagesNumber = allStats.getValue(SM_COMP, QueueType.IN_QUEUE.name() +
							" messages", 0L);
			messagesNumber += allStats.getValue(SM_COMP, QueueType.OUT_QUEUE.name() +
							" messages", 0L);
			presencesNumber = allStats.getValue(SM_COMP, QueueType.IN_QUEUE.name() +
							" presences", 0L);
			presencesNumber += allStats.getValue(SM_COMP, QueueType.OUT_QUEUE.name() +
							" presences", 0L);
			queueSize = 0;
			queueOverflow = 0;
			for (StatRecord rec : allStats) {
				if (rec.getDescription() == StatisticType.IN_QUEUE_OVERFLOW.getDescription() ||
								rec.getDescription() == StatisticType.OUT_QUEUE_OVERFLOW.getDescription()) {
					queueOverflow += rec.getLongValue();
				}
				if (rec.getDescription() == "Total In queues wait" ||
								rec.getDescription() == "Total Out queues wait") {
					queueSize += rec.getIntValue();
				}
			}
			//System.out.println("clusterPackets: " + clusterPackets +
			//				", smPackets: " + smPackets +
			//				", clientConnections: " + clientConnections);
		}

		private void updateSystemDetails() {
			StringBuilder sb = new StringBuilder();
			sb.append("Uptime: ").append(TigaseRuntime.getTigaseRuntime().getUptimeString());
			int cpu_temp = allStats.getValue("cpu-mon", "CPU temp", 0);
			if (cpu_temp > 0) {
				sb.append(",      Temp: ").append(cpu_temp).append(" C");
			}
			String cpu_freq = allStats.getValue("cpu-mon", "CPU freq", null);
			if (cpu_freq != null) {
				sb.append("\nFreq: ").append(cpu_freq);
			}
			String cpu_throt = allStats.getValue("cpu-mon", "CPU throt", null);
			if (cpu_throt != null) {
				sb.append("\nThrott: ").append(cpu_throt);
			}
			sb.append("\nThreads:");
			String cpu_thread = allStats.getValue("cpu-mon", "1st max CPU thread", null);
			if (cpu_thread != null) {
				sb.append("\n   ").append(cpu_thread);
			}
			cpu_thread = allStats.getValue("cpu-mon", "2nd max CPU thread", null);
			if (cpu_thread != null) {
				sb.append("\n   ").append(cpu_thread);
			}
			cpu_thread = allStats.getValue("cpu-mon", "3rd max CPU thread", null);
			if (cpu_thread != null) {
				sb.append("\n   ").append(cpu_thread);
			}
			LinkedHashMap<String, StatRecord> compStats = allStats.addCompStats(SM_COMP);
			for (StatRecord rec : compStats.values()) {
				if (rec.getDescription().startsWith("Processor:")) {
					sb.append("\n").append(rec.getDescription()).append(rec.getValue());
				}
			}
			systemDetails = sb.toString();
		}

	}

}


