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

package ch.unitelabs.mdns.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class AaaaRecord extends Record {
    private InetAddress address;

    public AaaaRecord(ByteBuffer buffer, String name, Class recordClass, long ttl) throws UnknownHostException {
        super(name, recordClass, ttl);
        byte[] addressBytes = new byte[16];
        buffer.get(addressBytes);
        address = InetAddress.getByAddress(addressBytes);
    }

    public InetAddress getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "AaaaRecord{" +
                "name='" + name + '\'' +
                ", recordClass=" + recordClass +
                ", ttl=" + ttl +
                ", address=" + address +
                '}';
    }
}
