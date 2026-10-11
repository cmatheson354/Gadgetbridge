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
package nodomain.freeyourgadget.gadgetbridge.service.devices.gloryfit;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;

import nodomain.freeyourgadget.gadgetbridge.devices.gloryfit.GloryFitConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;

/**
 * BLE device support for Fempoin T70 / GloryFit watches.
 *
 * Protocol captured via HCI snoop 2026-10-10.
 * Services: 0x55ff (data) and 0x56ff (secondary).
 * Write to 0x33f1, receive notifications on 0x33f2.
 */
public class GloryFitDeviceSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GloryFitDeviceSupport.class);

    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic2;
    private BluetoothGattCharacteristic notifyCharacteristic2;

    public GloryFitDeviceSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GloryFitConstants.UUID_SERVICE_PRIMARY);
        addSupportedService(GloryFitConstants.UUID_SERVICE_SECONDARY);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        writeCharacteristic  = getCharacteristic(GloryFitConstants.UUID_CHAR_WRITE);
        notifyCharacteristic = getCharacteristic(GloryFitConstants.UUID_CHAR_NOTIFY);
        writeCharacteristic2  = getCharacteristic(GloryFitConstants.UUID_CHAR_WRITE2);
        notifyCharacteristic2 = getCharacteristic(GloryFitConstants.UUID_CHAR_NOTIFY2);

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        // Subscribe to notifications on both notify characteristics
        builder.notify(notifyCharacteristic, true);
        builder.notify(notifyCharacteristic2, true);

        // Sync time then get device info and battery
        syncTime(builder);
        requestDeviceInfo(builder);
        requestBattery(builder);

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
        return builder;
    }

    private void syncTime(TransactionBuilder builder) {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        builder.write(writeCharacteristic, new byte[]{
                GloryFitConstants.CMD_TIME_SYNC,
                (byte) ((year >> 8) & 0xff),
                (byte) (year & 0xff),
                (byte) (c.get(Calendar.MONTH) + 1),
                (byte) c.get(Calendar.DAY_OF_MONTH),
                (byte) c.get(Calendar.HOUR_OF_DAY),
                (byte) c.get(Calendar.MINUTE),
                (byte) c.get(Calendar.SECOND)
        });
    }

    private void requestDeviceInfo(TransactionBuilder builder) {
        builder.write(writeCharacteristic, new byte[]{GloryFitConstants.CMD_DEVICE_INFO});
    }

    private void requestBattery(TransactionBuilder builder) {
        builder.write(writeCharacteristic, new byte[]{GloryFitConstants.CMD_BATTERY});
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        if (data == null || data.length == 0) return false;

        byte cmd = data[0];
        switch (cmd) {
            case GloryFitConstants.CMD_DEVICE_INFO:
                handleDeviceInfo(data);
                return true;
            case GloryFitConstants.CMD_BATTERY:
                handleBattery(data);
                return true;
            case GloryFitConstants.CMD_TIME_SYNC:
                LOG.debug("Time sync acknowledged");
                return true;
            case GloryFitConstants.CMD_FETCH_STEPS:
                handleStepRecord(data);
                return true;
            default:
                LOG.debug("Unhandled notification cmd=0x{}", String.format("%02x", cmd));
                return false;
        }
    }

    private void handleDeviceInfo(byte[] data) {
        // Response to 0xa1: payload is ASCII device serial
        if (data.length > 1) {
            String info = new String(data, 1, data.length - 1).trim();
            LOG.info("GloryFit device info: {}", info);
            getDevice().setFirmwareVersion(info);
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void handleBattery(byte[] data) {
        // Response to 0xbb: data[1] = battery % (observed: 0xbb 0x01 with extra bytes)
        if (data.length >= 2) {
            int level = data[1] & 0xff;
            if (level <= 100) {
                getDevice().setBatteryLevel(level);
                getDevice().sendDeviceUpdateIntent(getContext());
                LOG.info("GloryFit battery: {}%", level);
            }
        }
    }

    private void handleStepRecord(byte[] data) {
        // Format: b2 YY YY MM DD HH ss ss ...
        // YY YY = year (big-endian), MM = month, DD = day, HH = hour
        // ss ss = steps (little-endian, bytes 6-7 from index 1)
        if (data.length < 8) return;
        int year  = ((data[1] & 0xff) << 8) | (data[2] & 0xff);
        int month = data[3] & 0xff;
        int day   = data[4] & 0xff;
        int hour  = data[5] & 0xff;
        int steps = (data[7] & 0xff) << 8 | (data[6] & 0xff); // little-endian
        LOG.info("GloryFit steps: {}-{:02d}-{:02d} {:02d}:00 -> {} steps",
                year, month, day, hour, steps);
        // TODO: persist to activity database in a follow-up
    }

    @Override
    public void onSetTime() {
        try {
            TransactionBuilder builder = performInitialized("setTime");
            syncTime(builder);
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("Failed to set time on GloryFit watch", e);
        }
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        try {
            TransactionBuilder builder = performInitialized("fetchSteps");
            // Send b2 fa to request full step history
            builder.write(writeCharacteristic, new byte[]{
                    GloryFitConstants.CMD_FETCH_STEPS,
                    GloryFitConstants.SUBCMD_FETCH_ALL
            });
            builder.queue(getQueue());
        } catch (IOException e) {
            LOG.warn("Failed to fetch recorded data from GloryFit watch", e);
        }
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        // Push notification text to watch via handle 0x0018 (secondary write, cmd 0x46)
        // Format: 46 fa 05 NN LL <UTF-8 text>
        // Skipping for MVP
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        // Not implemented for MVP
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public boolean getImplicitCallbackModify() {
        return true;
    }

    @Override
    public boolean getSendWriteRequestResponse() {
        return false;
    }
}
