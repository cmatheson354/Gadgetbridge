/*  Copyright (C) 2026 Christopher Matheson

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.gloryfit;

import java.util.UUID;

import static nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport.BASE_UUID;

/**
 * Protocol constants for Fempoin T70 / GloryFit family watches.
 * Captured via HCI snoop from GloryFit Android app on 2026-10-10.
 *
 * Two services:
 *   0x55ff - primary data channel (write + notify)
 *   0x56ff - secondary channel (write-no-response + notify)
 */
public class GloryFitConstants {
    // Service UUIDs
    public static final UUID UUID_SERVICE_PRIMARY   = UUID.fromString(String.format(BASE_UUID, "55ff"));
    public static final UUID UUID_SERVICE_SECONDARY = UUID.fromString(String.format(BASE_UUID, "56ff"));

    // Characteristics on UUID_SERVICE_PRIMARY
    public static final UUID UUID_CHAR_WRITE  = UUID.fromString(String.format(BASE_UUID, "33f1")); // handle 0x0012, write-with-response
    public static final UUID UUID_CHAR_NOTIFY = UUID.fromString(String.format(BASE_UUID, "33f2")); // handle 0x0014, notify

    // Characteristics on UUID_SERVICE_SECONDARY
    public static final UUID UUID_CHAR_WRITE2  = UUID.fromString(String.format(BASE_UUID, "34f1")); // handle 0x0018, write-no-response
    public static final UUID UUID_CHAR_NOTIFY2 = UUID.fromString(String.format(BASE_UUID, "34f2")); // handle 0x001a, notify

    // Commands (first byte sent to UUID_CHAR_WRITE)
    public static final byte CMD_DEVICE_INFO   = (byte) 0xa1;
    public static final byte CMD_HEARTBEAT     = (byte) 0xa2;
    public static final byte CMD_TIME_SYNC     = (byte) 0xa3; // a3 YY YY MM DD HH MM SS
    public static final byte CMD_BATTERY       = (byte) 0xbb;
    public static final byte CMD_FETCH_STEPS   = (byte) 0xb2; // b2 fa = request all step history
    public static final byte CMD_FETCH_HR      = (byte) 0xf7; // f7 fa YY YY MM DD HH mm
    public static final byte CMD_FETCH_SLEEP   = (byte) 0xfd;
    public static final byte SUBCMD_FETCH_ALL  = (byte) 0xfa; // used as second byte for "fetch all"

    // Device name prefix used in BLE advertisement
    public static final String DEVICE_NAME_PREFIX = "T70(ID-";
}
