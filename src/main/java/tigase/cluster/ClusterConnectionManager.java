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

package tigase.cluster;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Queue;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.UnknownHostException;

import tigase.net.ConnectionType;
//import tigase.net.IOService;
import tigase.net.SocketType;
import tigase.server.ConnectionManager;
import tigase.server.MessageReceiver;
import tigase.server.Packet;
import tigase.server.MessageRouter;
import tigase.disco.XMPPService;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.util.Algorithms;
import tigase.util.JIDUtils;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.Authorization;
import tigase.xmpp.XMPPIOService;
import tigase.xmpp.PacketErrorTypeException;

/**
 * Class ClusterConnectionManager
 *
 * Created: Tue Nov 22 07:07:11 2005
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class ClusterConnectionManager extends ConnectionManager<XMPPIOService>
	implements XMPPService {

	public int[] PORTS = {5277};
  public static final String SECRET_PROP_KEY = "secret";
  public String SECRET_PROP_VAL =	"someSecret";
	public static final String PORT_LOCAL_HOST_PROP_KEY = "local-host";
	public String PORT_LOCAL_HOST_PROP_VAL = "localhost";
	public String PORT_REMOTE_HOST_PROP_VAL = "comp-1.localhost";
	public static final String PORT_ROUTING_TABLE_PROP_KEY = "routing-table";
	public String[] PORT_IFC_PROP_VAL = {"*"};
	public static final String RETURN_SERVICE_DISCO_KEY = "service-disco";
	public static final boolean RETURN_SERVICE_DISCO_VAL = true;
	public static final String IDENTITY_TYPE_KEY = "identity-type";
	public static final String IDENTITY_TYPE_VAL = "generic";
	public static final String CONNECT_ALL_PAR = "--cluster-connect-all";
	public static final String CONNECT_ALL_PROP_KEY = "connect-all";
	public static final String CLUSTER_CONTR_ID_PROP_KEY = "cluster-controller-id";
	//	public static final String NOTIFY_ADMINS_PROP_KEY = "notify-admins";
	//	public static final boolean NOTIFY_ADMINS_PROP_VAL = true;
	public static final boolean CONNECT_ALL_PROP_VAL = false;
	public static final String XMLNS = "tigase:cluster";

	private ServiceEntity serviceEntity = null;
	//private boolean service_disco = RETURN_SERVICE_DISCO_VAL;
	private String identity_type = IDENTITY_TYPE_VAL;
	private boolean connect_all = CONNECT_ALL_PROP_VAL;
	//	private boolean notify_admins = NOTIFY_ADMINS_PROP_VAL;
	//	private String[] admins = new String[] {};
	private String cluster_controller_id = null;

  /**
   * Variable <code>log</code> is a class logger.
   */
  private static final Logger log =
    Logger.getLogger("tigase.cluster.ClusterConnectionManager");

	public void processPacket(Packet packet) {
		log.finer("Processing packet: " + packet.getElemName()
			+ ", type: " + packet.getType());
		log.finest("Processing packet: " + packet.getStringData());
		if (packet.getElemTo() != null
			&& packet.getElemTo().equals(getComponentId())) {
			try {
				addOutPacket(
					Authorization.FEATURE_NOT_IMPLEMENTED.getResponseMessage(packet,
						"Not implemented", true));
			} catch (PacketErrorTypeException e) {
				log.warning("Packet processing exception: " + e);
			}
			return;
		}
		writePacketToSocket(packet);
	}

	public Queue<Packet> processSocketData(XMPPIOService serv) {
		Packet p = null;
		while ((p = serv.getReceivedPackets().poll()) != null) {
			log.finer("Processing packet: " + p.getElemName()
				+ ", type: " + p.getType());
			log.finest("Processing socket data: " + p.getStringData());
			if (p.getElemName().equals("handshake")) {
				processHandshake(p, serv);
			} else {
				addOutPacket(p);
			}
		} // end of while ()
		return null;
	}

	private void processHandshake(Packet p, XMPPIOService serv) {
		switch (serv.connectionType()) {
		case connect: {
			String data = p.getElemCData();
			if (data == null) {
				serviceConnected(serv);
			} else {
				log.warning("Incorrect packet received: " + p.getStringData());
			}
			break;
		}
		case accept: {
			String digest = p.getElemCData();
			String id =
				(String)serv.getSessionData().get(serv.SESSION_ID_KEY);
			String secret =
				(String)serv.getSessionData().get(SECRET_PROP_KEY);
			try {
				String loc_digest = Algorithms.hexDigest(id, secret, "SHA");
				log.finest("Calculating digest: id="+id+", secret="+secret
					+", digest="+loc_digest);
				if (digest != null && digest.equals(loc_digest)) {
					Packet resp = new Packet(new Element("handshake"));
					writePacketToSocket(serv, resp);
					serviceConnected(serv);
				} else {
					log.info("Handshaking password doesn't match, disconnecting...");
					serv.stop();
				}
			} catch (Exception e) {
				log.log(Level.SEVERE, "Handshaking error.", e);
			}
			break;
		}
		default:
			// Do nothing, more data should come soon...
			break;
		} // end of switch (service.connectionType())
	}

	protected void serviceConnected(XMPPIOService serv) {
		String[] routings =
			(String[])serv.getSessionData().get(PORT_ROUTING_TABLE_PROP_KEY);
		updateRoutings(routings, true);
		String addr =
			(String)serv.getSessionData().get(PORT_REMOTE_HOST_PROP_KEY);
		log.fine("Connected to: " + addr);
		updateServiceDiscovery(addr, "XEP-0114 connected");
		addOutPacket(new Packet(ClusterElement.createClusterConnectedNodes(
					getComponentId(), cluster_controller_id,
					StanzaType.set.toString(), addr)));
	}

	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);
		//service_disco = (Boolean)props.get(RETURN_SERVICE_DISCO_KEY);
		identity_type = (String)props.get(IDENTITY_TYPE_KEY);

		//serviceEntity = new ServiceEntity(getName(), "external", "XEP-0114");
		serviceEntity = new ServiceEntity("XEP-0114 " + getName(), null, "XEP-0114");
		serviceEntity.addIdentities(
			new ServiceIdentity("component", identity_type, "XEP-0114 " + getName()));
		connect_all = (Boolean)props.get(CONNECT_ALL_PROP_KEY);
		cluster_controller_id = (String)props.get(CLUSTER_CONTR_ID_PROP_KEY);
// 		notify_admins = (Boolean)props.get(NOTIFY_ADMINS_PROP_KEY);
// 		admins = (String[])props.get(ADMINS_PROP_KEY);
		connectionDelay = 30*SECOND;
		String[] cl_nodes = (String[])props.get(CLUSTER_NODES_PROP_KEY);
		if (cl_nodes != null) {
			for (String node: cl_nodes) {
				String host = JIDUtils.getNodeHost(node);
				log.config("Found cluster node host: " + host);
				if (!host.equals(getDefHostName())
					&& (host.hashCode() > getDefHostName().hashCode() || connect_all)) {
					log.config("Trying to connect to cluster node: " + host);
					Map<String, Object> port_props = new LinkedHashMap<String, Object>();
					port_props.put(SECRET_PROP_KEY, SECRET_PROP_VAL);
					port_props.put(PORT_LOCAL_HOST_PROP_KEY, getDefHostName());
					port_props.put(PORT_TYPE_PROP_KEY, ConnectionType.connect);
					port_props.put(PORT_SOCKET_PROP_KEY, SocketType.plain);
					port_props.put(PORT_REMOTE_HOST_PROP_KEY, host);
					port_props.put(PORT_IFC_PROP_KEY, new String[] {host});
					port_props.put(MAX_RECONNECTS_PROP_KEY, 99999999);
					port_props.put(PORT_KEY, PORTS[0]);
					reconnectService(port_props, connectionDelay);
				}
			}
		}
	}

	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);
		props.put(RETURN_SERVICE_DISCO_KEY, RETURN_SERVICE_DISCO_VAL);
		props.put(IDENTITY_TYPE_KEY, IDENTITY_TYPE_VAL);
		if (params.get(CONNECT_ALL_PAR) == null
			|| !((String)params.get(CONNECT_ALL_PAR)).equals("true")) {
			props.put(CONNECT_ALL_PROP_KEY, false);
		} else {
			props.put(CONNECT_ALL_PROP_KEY, true);
		}
		if (params.get(CLUSTER_NODES) != null) {
			String[] cl_nodes = ((String)params.get(CLUSTER_NODES)).split(",");
			for (int i = 0; i < cl_nodes.length; i++) {
				cl_nodes[i] = JIDUtils.getNodeHost(cl_nodes[i]);
			}
			props.put(CLUSTER_NODES_PROP_KEY, cl_nodes);
		} else {
			props.put(CLUSTER_NODES_PROP_KEY, new String[] {getDefHostName()});
		}
		props.put(CLUSTER_CONTR_ID_PROP_KEY,
			DEF_CLUST_CONTR_NAME + "@" + getDefHostName());
// 		props.put(NOTIFY_ADMINS_PROP_KEY, NOTIFY_ADMINS_PROP_VAL);
// 		if (params.get(GEN_ADMINS) != null) {
// 			admins = ((String)params.get(GEN_ADMINS)).split(",");
// 		} else {
// 			admins = new String[] { "admin@localhost" };
// 		}
// 		props.put(ADMINS_PROP_KEY, admins);
		return props;
	}

	protected Map<String, Object> getParamsForPort(int port) {
    Map<String, Object> defs = new LinkedHashMap<String, Object>();
		defs.put(SECRET_PROP_KEY, SECRET_PROP_VAL);
		defs.put(PORT_TYPE_PROP_KEY, ConnectionType.accept);
		defs.put(PORT_SOCKET_PROP_KEY, SocketType.plain);
		defs.put(PORT_IFC_PROP_KEY, PORT_IFC_PROP_VAL);
		return defs;
	}

	protected int[] getDefPlainPorts() {
		return PORTS;
	}

	protected String getUniqueId(XMPPIOService serv) {
		//		return (String)serv.getSessionData().get(PORT_REMOTE_HOST_PROP_KEY);
		return serv.getRemoteAddress();
	}

	public void serviceStopped(XMPPIOService service) {
		super.serviceStopped(service);
		Map<String, Object> sessionData = service.getSessionData();
		String[] routings = (String[])sessionData.get(PORT_ROUTING_TABLE_PROP_KEY);
		if (routings != null) {
			updateRoutings(routings, false);
		}
		ConnectionType type = service.connectionType();
		if (type == ConnectionType.connect) {
			reconnectService(sessionData, connectionDelay);
		} // end of if (type == ConnectionType.connect)
		//		removeRouting(serv.getRemoteHost());
		String addr = (String)sessionData.get(PORT_REMOTE_HOST_PROP_KEY);
		log.fine("Disonnected from: " + addr);
		updateServiceDiscovery(addr, "XEP-0114 disconnected");
		addOutPacket(new Packet(ClusterElement.createClusterDisconnectedNodes(
					getComponentId(), cluster_controller_id,
					StanzaType.set.toString(), addr)));
	}

	protected String getServiceId(Packet packet) {
		try {
			return DNSResolver.getHostIP(JIDUtils.getNodeHost(packet.getTo()));
		} catch (UnknownHostException e) {
			log.warning("Uknown host exception for address: "
				+ JIDUtils.getNodeHost(packet.getTo()));
			return JIDUtils.getNodeHost(packet.getTo());
		}
	}

	public void serviceStarted(XMPPIOService serv) {
		super.serviceStarted(serv);
		log.finest("c2c connection opened: " + serv.getRemoteAddress()
			+ ", type: " + serv.connectionType().toString()
			+ ", id=" + serv.getUniqueId());
// 		String addr =
// 			(String)service.getSessionData().get(PORT_REMOTE_HOST_PROP_KEY);
// 		addRouting(addr);
		//		addRouting(serv.getRemoteHost());
		switch (serv.connectionType()) {
		case connect:
			// Send init xmpp stream here
			//XMPPIOService serv = (XMPPIOService)service;
			String remote_host =
        (String)serv.getSessionData().get(PORT_REMOTE_HOST_PROP_KEY);
			serv.getSessionData().put(serv.HOSTNAME_KEY, remote_host);
			serv.getSessionData().put(PORT_ROUTING_TABLE_PROP_KEY,
				new String[] {remote_host, ".*@" + remote_host, ".*\\." + remote_host});
			String data =
				"<stream:stream"
				+ " xmlns='" + XMLNS + "'"
				+ " xmlns:stream='http://etherx.jabber.org/streams'"
				+ " from='" + getDefHostName() + "'"
				+ " to='" + remote_host + "'"
				+ ">";
			log.finest("cid: " + (String)serv.getSessionData().get("cid")
				+ ", sending: " + data);
			serv.xmppStreamOpen(data);
			break;
		default:
			// Do nothing, more data should come soon...
			break;
		} // end of switch (service.connectionType())
	}

	public String xmppStreamOpened(XMPPIOService service,
		Map<String, String> attribs) {

		log.finer("Stream opened: " + attribs.toString());

		switch (service.connectionType()) {
		case connect: {
			String id = attribs.get("id");
			service.getSessionData().put(service.SESSION_ID_KEY, id);
			String secret =
				(String)service.getSessionData().get(SECRET_PROP_KEY);
			try {
				String digest = Algorithms.hexDigest(id, secret, "SHA");
				log.finest("Calculating digest: id="+id+", secret="+secret
					+", digest="+digest);
				return "<handshake>" + digest + "</handshake>";
			} catch (NoSuchAlgorithmException e) {
				log.log(Level.SEVERE, "Can not generate digest for pass phrase.", e);
				return null;
			}
		}
		case accept: {
			String remote_host = attribs.get("from");
			service.getSessionData().put(service.HOSTNAME_KEY, remote_host);
			service.getSessionData().put(PORT_ROUTING_TABLE_PROP_KEY,
				new String[] {remote_host, ".*@" + remote_host, ".*\\." + remote_host});
			String id = UUID.randomUUID().toString();
			service.getSessionData().put(service.SESSION_ID_KEY, id);
			return "<stream:stream"
				+ " xmlns='" + XMLNS + "'"
				+ " xmlns:stream='http://etherx.jabber.org/streams'"
				+ " from='" + getDefHostName() + "'"
				+ " to='" + remote_host + "'"
				+ " id='" + id + "'"
				+ ">";
		}
		default:
			// Do nothing, more data should come soon...
			break;
		} // end of switch (service.connectionType())
		return null;
	}

	public void xmppStreamClosed(XMPPIOService serv) {
		log.finer("Stream closed.");
	}

	/**
	 * Method <code>getMaxInactiveTime</code> returns max keep-alive time
	 * for inactive connection. we shoulnd not really close external component
	 * connection at all, so let's say something like: 1000 days...
	 *
	 * @return a <code>long</code> value
	 */
	protected long getMaxInactiveTime() {
		return 1000*24*HOUR;
	}

	private void updateRoutings(String[] routings, boolean add) {
		if (add) {
			for (String route: routings) {
				try {
					addRegexRouting(route);
				} catch (Exception e) {
					addRouting(route);
				}
			}
		} else {
			for (String route: routings) {
				try {
					removeRegexRouting(route);
				} catch (Exception e) {
					removeRouting(route);
				}
			}
		}
	}

	private void updateServiceDiscovery(String jid, String name) {
		ServiceEntity item = new ServiceEntity(jid, null, name);
		//item.addIdentities(new ServiceIdentity("component", identity_type, name));
		log.finest("Modifing service-discovery info: " + item.toString());
		serviceEntity.addItems(item);
	}

	public Element getDiscoInfo(String node, String jid) {
		if (jid != null && getName().equals(JIDUtils.getNodeNick(jid))) {
			return serviceEntity.getDiscoInfo(node);
		}
		return null;
	}

	public 	List<Element> getDiscoFeatures() { return null; }

	public List<Element> getDiscoItems(String node, String jid) {
		if (getName().equals(JIDUtils.getNodeNick(jid))) {
			return serviceEntity.getDiscoItems(node, null);
		} else {
 			return Arrays.asList(serviceEntity.getDiscoItem(null,
					JIDUtils.getNodeID(getName(), jid)));
		}
	}

	protected XMPPIOService getXMPPIOServiceInstance() {
		return new XMPPIOService();
	}

}
