package com.printshop.infra.gateway;

import org.springframework.stereotype.Component;

/**
 * 机台扫码/MQTT 防腐层占位适配器。
 */
@Component
public class DeviceGatewayAdapter {

    public boolean dispatchToDevice(String deviceSn) {
        return deviceSn != null && !deviceSn.isBlank();
    }
}
