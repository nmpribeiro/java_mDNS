package ch.unitelabs.mdns.sd;

import ch.unitelabs.mdns.dns.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class Discovery {

    public static class Device {
        String name;
        long ttl;
        Map<String, String> attributes;
        String identifier;
        int port;
        String host;
        Response response;

        @Override
        public String toString() {
            return "\nDevice{" +
                    "name='" + name + '\'' +
                    ", ttl=" + ttl +
                    ", identifier=" + identifier +
                    ", host=" + host +
                    ", port=" + port +
                    // ", response=" + response +
                    ", attributes=" + attributes +
                    '}' + '\n';
        }
    }

    private final static Logger logger = LoggerFactory.getLogger(Discovery.class);

    public HashMap<String, Discovery.Device> cache = new HashMap<>();

    private int port = 5353;
    byte[] buffer = new byte[65509];
    private InetAddress ia, ia1, ia2;

    private static String NAME = "_tcp.";

    private static final String MDNS_IP4_ADDRESS = "224.0.0.251";
    private static final String MDNS_IP6_ADDRESS = "FF02::FB";

    public Discovery() {
        // nothing
    }

    public Discovery(String name) {
        this.NAME = name;
    }

    public void run() {
        try {
            ia = getLocalHostLANAddress();
            run(ia);
        } catch (UnknownHostException e) {
            logger.error("UnknownHostException" + e.getMessage());
        }
    }
    public void run(InetAddress ia) {
        this.ia = ia;

        logger.debug("Using " + ia.getHostAddress() + ":5353 to send mDNS initial query");

        try {
            // ia = getInetAddress("local");
            ia1 = InetAddress.getByName(MDNS_IP4_ADDRESS);
            ia2 = InetAddress.getByName(MDNS_IP6_ADDRESS);
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }

        try {
            // Create a socket to listen on the port.
            MulticastSocket ms = new MulticastSocket(port);
            if (ia == null) {
                ia = getLocalHostLANAddress();
            }

            // Join Multicast Socket to Multicast Addresses IPv4 and IPv6
            if (ia1 != null) ms.joinGroup(ia1);
            if (ia2 != null) ms.joinGroup(ia2);

            // Create a DatagramPacket packet to receive data into the buffer
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ia, port);

            // Do question on Network!
            Service service = Service.fromName(NAME);
            Query query = Query.createFor(service, Domain.LOCAL);

            try {
                Set<Instance> instances = query.runOnceOn(ia);
                instances.stream().forEach((instance) -> {
                    System.out.println( instance.toString() );
                    Device device = new Device();
                    device.identifier = instance.getName();
                    device.host = instance.getAddresses().toString();
                    device.port = instance.getPort();
                    // cache.put(device.identifier, device);
                });
            } catch (Exception e) {
                logger.error(e.getMessage());
            }

            while (true) {
                // Wait to receive a datagram
                ms.receive(packet);
                String str = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );
                // logger.info("UDP >> " + packet.getAddress() + ":" + packet.getPort() + " DATA: " + str);

                Response response = Response.createFrom(packet);

                logger.debug(response.toString());

                String deviceName = null;
                String identifier = null;
                Map<String, String> attributes = new HashMap<>();
                int devicePort = 0;
                long ttl = 0;
                boolean found = false;

                for (Record record : response.getRecords()) {


                    if (record instanceof PtrRecord) {
                        deviceName = ((PtrRecord) record).getUserVisibleName();
                    }

                    if (record instanceof TxtRecord) {
                        attributes = ((TxtRecord) record).getAttributes();
                    }

                    if (record instanceof SrvRecord) {
                        devicePort = ((SrvRecord) record).getPort();
                    }

                    if (record.getName().contains(NAME) && !record.getName().equals(NAME + "local.")) {
                        found = true;
                    }

                    identifier = record.getName();
                    ttl = record.getTTL();
                }

                if (found) {
                    Discovery.Device device;
                    device = cache.get( deviceName );
                    if (device == null) {
                        device = new Discovery.Device();
                    }
                    device.name = deviceName;
                    device.ttl = ttl;
                    device.host = packet.getAddress().getHostAddress();
                    device.port = devicePort;
                    device.identifier = identifier;
                    device.attributes = attributes;
                    device.response = response;
                    if (device.name != null) {
                        if (ttl == 0) cache.remove(device.name);
                        else cache.put(device.name, device);
                    }
                    // System.out.println("DEVICE >> " + device.name );

                    // logger.info("TYPE >> " + device.attributes );
                    // logger.info("DEVICE >> " + device );
                    logger.info("CACHE >> size:" + cache.size() + " \n" + cache );
                }

                // Reset the length of the packet before reusing it.
                packet.setLength(buffer.length);
            }
        }
        catch (IOException se) {
            // System.err.println("IO Exception " +  se);
            logger.error("IO Exception " + se.getMessage());
        }
    }

    /*

    How does a whole PC goes out by turning their:
        1. lid out
        2. PC shuts down network

    Response{
        questions=[],
        records=[
            PtrRecord{
                name='1.9.d.4.7.e.d.4.6.b.a.7.1.e.4.d.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.',
                recordClass=IN,
                ttl=0,
                ptrName='demo-2.local.'
            },
            AaaaRecord{
                name='demo-2.local.',
                recordClass=IN,
                ttl=0,
                address=/0:0:0:0:d4e1:7ab6:4de7:4d91
            }
        ],
        numQuestions=0,
        numAnswers=2,
        numNameServers=0,
        numAdditionalRecords=0
    }

     */

    /**
     * Gets IP Address that is assigned on a given network interface (currently
     * only supports IPv4)
     *
     * @param interfaceName Name of network interface
     */
    public static InetAddress getInetAddress(String interfaceName) throws SocketException, UnknownHostException {
        try {
            if (interfaceName.matches("local")) {
                return InetAddress.getLocalHost();
            }
            final String interfaceDisplay = "Network interface " + interfaceName;
            final NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
            if (networkInterface == null) {
                StringBuffer networkInterfaceNames = new StringBuffer();
                try {
                    for (NetworkInterface nInterface : Collections
                            .list(NetworkInterface.getNetworkInterfaces())) {
                        networkInterfaceNames.append(nInterface.getName() + ", ");
                    }
                } catch (SocketException e) {
                    throw new RuntimeException(e.getMessage());
                }

                throw new ConnectException(interfaceDisplay + " doesn't exist on this machine\n"
                        + "Use one of these instead " + networkInterfaceNames.toString());
            }
            if (!networkInterface.isUp())
                throw new ConnectException(interfaceDisplay + " is not up");
            if (networkInterface.isPointToPoint())
                throw new ConnectException(interfaceDisplay + " can not be PtP");

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            if (!addresses.hasMoreElements())
                throw new ConnectException(interfaceDisplay + " doesnt have assigned IPs");

            for (InetAddress address : Collections.list(addresses)) {
                // @TODO: For now only support IPv4 - check IPv6
                if (address instanceof Inet4Address)
                    return address;
            }
            throw new ConnectException(interfaceDisplay + " doesn't have an assigned IPv4 address");
        } catch (SocketException | UnknownHostException e) {
            throw e;
        }
    }


    /**
     * Returns an <code>InetAddress</code> object encapsulating what is most likely the machine's LAN IP address.
     * <p/>
     * This method is intended for use as a replacement of JDK method <code>InetAddress.getLocalHost</code>, because
     * that method is ambiguous on Linux systems. Linux systems enumerate the loopback network interface the same
     * way as regular LAN network interfaces, but the JDK <code>InetAddress.getLocalHost</code> method does not
     * specify the algorithm used to select the address returned under such circumstances, and will often return the
     * loopback address, which is not valid for network communication. Details
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037">here</a>.
     * <p/>
     * This method will scan all IP addresses on all network interfaces on the host machine to determine the IP address
     * most likely to be the machine's LAN address. If the machine has multiple IP addresses, this method will prefer
     * a site-local IP address (e.g. 192.168.x.x or 10.10.x.x, usually IPv4) if the machine has one (and will return the
     * first site-local address if the machine has more than one), but if the machine does not hold a site-local
     * address, this method will return simply the first non-loopback address found (IPv4 or IPv6).
     * <p/>
     * If this method cannot find a non-loopback address using this selection algorithm, it will fall back to
     * calling and returning the result of JDK method <code>InetAddress.getLocalHost</code>.
     * <p/>
     *
     * @throws UnknownHostException If the LAN address of the machine cannot be found.
     */
    private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            // Iterate all NICs (network interface cards)...
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {

                        if (inetAddr.isSiteLocalAddress()) {
                            // Found non-loopback site-local address. Return it immediately...
                            return inetAddr;
                        }
                        else if (candidateAddress == null) {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local address is not subsequently found...
                            candidateAddress = inetAddr;
                            // Note that we don't repeatedly assign non-loopback non-site-local addresses as candidates,
                            // only the first. For subsequent iterations, candidate will be non-null.
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                // We did not find a site-local address, but we found some other non-loopback address.
                // Server might have a non-site-local address assigned to its NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                // Return this non-loopback candidate address...
                return candidateAddress;
            }
            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            logger.info(jdkSuppliedAddress.toString());
            return jdkSuppliedAddress;
        }
        catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}