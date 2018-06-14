/*
 * Copyright 2015 Todd Kulesza <todd@dropline.net>.
 *
 * This file is part of Hola.
 *
 * Hola is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hola is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hola.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.unitelabs.mdns.sd;

import ch.unitelabs.mdns.dns.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

public class Instance {
    private final String name;
    private final Set<InetAddress> addresses;
    private final int port;
    public final Map<String, String> attributes;
    public Long ttl;
    public String host;

    private final static Logger logger = LoggerFactory.getLogger(Instance.class);

    static Instance createFromRecords(PtrRecord ptr, Set<Record> records) {
        String name = ptr.getUserVisibleName();
        int port;
        long ttl;
        List<InetAddress> addresses = new ArrayList<>();
        Map<String, String> attributes = Collections.emptyMap();

        Optional<SrvRecord> srv = records.stream()
                .filter(r -> r instanceof SrvRecord && r.getName().equals(ptr.getPtrName()))
                .map(r -> (SrvRecord) r).findFirst();
        if (srv.isPresent()) {
            // records.f
            // logger.debug("Using SrvRecord {} to create instance for {}", srv, ptr);
            ttl = srv.get().getTTL();
            port = srv.get().getPort();
            addresses.addAll(records.stream().filter(r -> r instanceof ARecord)
                    .filter(r -> r.getName().equals(srv.get().getTarget())).map(r -> ((ARecord) r).getAddress())
                    .collect(Collectors.toList()));
            addresses.addAll(records.stream().filter(r -> r instanceof AaaaRecord)
                    .filter(r -> r.getName().equals(srv.get().getTarget())).map(r -> ((AaaaRecord) r).getAddress())
                    .collect(Collectors.toList()));
        } else {
            // throw new IllegalStateException("Cannot create Instance when no SRV record is available");
            logger.error("Cannot create Instance when no SRV record is available");
            return null;
        }
        Optional<TxtRecord> txt = records.stream()
                .filter(r -> r instanceof TxtRecord && r.getName().equals(ptr.getPtrName()))
                .map(r -> (TxtRecord) r).findFirst();
        if (txt.isPresent()) {
            // logger.debug("Using TxtRecord {} to create attributes for {}", txt, ptr);
            attributes = txt.get().getAttributes();
            ttl = srv.get().getTTL();
        }

        return new Instance(name, addresses, port, attributes, ttl);
    }

    Instance(String name, List<InetAddress> addresses, int port, Map<String, String> attributes, Long ttl) {
        this.name = name;
        this.ttl = ttl;
        this.addresses = new HashSet<>();
        this.addresses.addAll(addresses);
        this.port = port;
        this.attributes = attributes;
    }

    /**
     * Get the user-visible name associated with this instance.
     * <p>
     * This value comes from the instance's PTR record.
     *
     * @return name
     */
    @SuppressWarnings("unused")
    public String getName() {
        return name;
    }

    /**
     * Get the set of IP addresses associated with this instance.
     * <p>
     * These values come from the instance's A and AAAA records.
     *
     * @return set of addresses
     */
    @SuppressWarnings("unused")
    public Set<InetAddress> getAddresses() {
        return Collections.unmodifiableSet(addresses);
    }

    /**
     * Get the port number associated with this instance.
     * <p>
     * This value comes from the instance's SRV record.
     *
     * @return port number
     */
    @SuppressWarnings("unused")
    public int getPort() {
        return port;
    }

    /**
     * Check whether this instance has the specified attribute.
     * <p>
     * Attributes come from the instance's TXT records.
     *
     * @param attribute name of the attribute to search for
     * @return true if the instance has a value for attribute, false otherwise
     */
    @SuppressWarnings("unused")
    public boolean hasAttribute(String attribute) {
        return attributes.containsKey(attribute);
    }

    /**
     * Get the value of the specified attribute.
     * <p>
     * Attributes come from the instance's TXT records.
     *
     * @param attribute name of the attribute to search for
     * @return value of the given attribute, or null if the attribute doesn't exist in this Instance
     */
    @SuppressWarnings("unused")
    public String lookupAttribute(String attribute) {
        return attributes.get(attribute);
    }

    @Override
    public String toString() {
        return "{" +
                "name='" + name + '\'' +
                ", addresses=" + addresses +
                ", host=" + host +
                ", port=" + port +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + getName().hashCode();
        result = 31 * result + getPort();
        for (InetAddress address : getAddresses()) {
            result = 31 * result + address.hashCode();
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            result = 31 * result + entry.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Instance)) {
            return false;
        }
        Instance other = (Instance) obj;
        if (!getName().equals(other.getName())) {
            return false;
        }
        if (getPort() != other.getPort()) {
            return false;
        }
        for (InetAddress address : getAddresses()) {
            if (!other.getAddresses().contains(address)) {
                return false;
            }
        }
        for (InetAddress address : other.getAddresses()) {
            if (!getAddresses().contains(address)) {
                return false;
            }
        }
        for (String key : attributes.keySet()) {
            if (!other.hasAttribute(key) || !other.lookupAttribute(key).equals(lookupAttribute(key))) {
                return false;
            }
        }
        for (String key : other.attributes.keySet()) {
            if (!hasAttribute(key) || !lookupAttribute(key).equals(other.lookupAttribute(key))) {
                return false;
            }
        }
        return true;
    }
}
