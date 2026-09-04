import { describeDevice, printTestReceipt } from "./receipt.js";

const state = {
  status: { connected: false, connectionState: "disconnected" },
  bleDevices: [],
  usbDevices: [],
  selectedBleId: "",
  selectedUsbId: "",
  scanning: false,
  busy: "",
  log: [],
};

const els = {
  statusConnection: document.querySelector("#status-connection"),
  statusTransport: document.querySelector("#status-transport"),
  statusPrinter: document.querySelector("#status-printer"),
  statusReconnect: document.querySelector("#status-reconnect"),
  statusError: document.querySelector("#status-error"),
  bleList: document.querySelector("#ble-list"),
  usbList: document.querySelector("#usb-list"),
  banner: document.querySelector("#banner"),
  log: document.querySelector("#log"),
  scanBle: document.querySelector("#scan-ble"),
  stopBle: document.querySelector("#stop-ble"),
  connectBle: document.querySelector("#connect-ble"),
  listUsb: document.querySelector("#list-usb"),
  connectUsb: document.querySelector("#connect-usb"),
  printTest: document.querySelector("#print-test"),
  disconnect: document.querySelector("#disconnect"),
  refreshStatus: document.querySelector("#refresh-status"),
  clearLog: document.querySelector("#clear-log"),
};

init().catch((error) => {
  setBanner(readError(error), true);
  pushLog(readError(error), true);
});

async function init() {
  bindActions();
  render();

  const plugin = getPlugin();
  if (!plugin) {
    setBanner("Capacitor plugin not found. Run this page inside an Android Capacitor shell with JenixThermalPrinter installed.", true);
    return;
  }

  setBanner("Plugin ready. Refresh status or start a scan.");
  await registerListeners(plugin);
  await refreshStatus();
  await listUsbDevices();
}

function bindActions() {
  els.scanBle?.addEventListener("click", () => void startBleScan());
  els.stopBle?.addEventListener("click", () => void stopBleScan());
  els.connectBle?.addEventListener("click", () => void connectBle());
  els.listUsb?.addEventListener("click", () => void listUsbDevices());
  els.connectUsb?.addEventListener("click", () => void connectUsb());
  els.printTest?.addEventListener("click", () => void printDemoReceipt());
  els.disconnect?.addEventListener("click", () => void disconnectPrinter());
  els.refreshStatus?.addEventListener("click", () => void refreshStatus());
  els.clearLog?.addEventListener("click", () => {
    state.log = [];
    renderLog();
  });
}

async function registerListeners(plugin) {
  const handles = await Promise.all([
    plugin.addListener("deviceFound", (device) => {
      state.bleDevices = upsertDevice(state.bleDevices, device);
      state.selectedBleId = state.selectedBleId || device.id || "";
      pushLog(`BLE device found: ${describeDevice(device)}`);
      render();
    }),
    plugin.addListener("scanStopped", (event) => {
      state.scanning = false;
      state.bleDevices = Array.isArray(event.devices) ? event.devices : [];
      state.selectedBleId = keepSelectedId(state.selectedBleId, state.bleDevices);
      pushLog(`BLE scan stopped: ${event.reason || "unknown"} (${state.bleDevices.length} device(s))`);
      render();
    }),
    plugin.addListener("connected", (status) => {
      state.status = status;
      pushLog(`Connected: ${describeDevice(status.device)}`);
      setBanner(`Connected to ${describeDevice(status.device)}.`);
      render();
    }),
    plugin.addListener("disconnected", (status) => {
      state.status = status;
      pushLog("Printer disconnected.");
      setBanner("Printer disconnected.");
      render();
    }),
    plugin.addListener("connectionError", (event) => {
      pushLog(`${(event.transport || "printer").toUpperCase()}: ${event.message}`, true);
      setBanner(event.message, true);
      void refreshStatus();
    }),
    plugin.addListener("usbAttached", (device) => {
      state.usbDevices = upsertDevice(state.usbDevices, device);
      state.selectedUsbId = state.selectedUsbId || device.id || "";
      pushLog(`USB attached: ${describeDevice(device)}`);
      render();
    }),
    plugin.addListener("usbDetached", (device) => {
      state.usbDevices = state.usbDevices.filter((item) => item.id !== device.id);
      state.selectedUsbId = keepSelectedId(state.selectedUsbId, state.usbDevices);
      pushLog(`USB detached: ${describeDevice(device)}`, true);
      void refreshStatus();
      render();
    }),
  ]);

  window.addEventListener("beforeunload", () => {
    void Promise.all(handles.map((handle) => handle.remove()));
  });
}

async function startBleScan() {
  await runAction("Scanning BLE devices...", async (plugin) => {
    state.scanning = true;
    state.bleDevices = [];
    state.selectedBleId = "";
    render();
    const result = await plugin.scan({ transport: "ble", allowUnnamed: true, timeoutMs: 10000 });
    state.bleDevices = Array.isArray(result.devices) ? result.devices : [];
    state.selectedBleId = keepSelectedId(state.selectedBleId, state.bleDevices);
    pushLog(`BLE scan completed with ${state.bleDevices.length} device(s).`);
  }, { clearBusyOnError: true, clearBusyOnSuccess: true });
  state.scanning = false;
  render();
}

async function stopBleScan() {
  await runAction("Stopping BLE scan...", async (plugin) => {
    await plugin.stopScan();
    state.scanning = false;
    pushLog("BLE scan stopped manually.");
  });
}

async function connectBle() {
  await runAction("Connecting BLE printer...", async (plugin) => {
    if (!state.selectedBleId) {
      throw new Error("Select a BLE printer first.");
    }
    state.status = await plugin.connect({
      transport: "ble",
      deviceId: state.selectedBleId,
      autoReconnect: true,
      reconnectAttempts: 2,
      reconnectDelayMs: 1500,
      timeoutMs: 15000,
    });
  });
}

async function listUsbDevices() {
  await runAction("Listing USB devices...", async (plugin) => {
    const result = await plugin.getDevices({ transport: "usb" });
    state.usbDevices = Array.isArray(result.devices) ? result.devices : [];
    state.selectedUsbId = keepSelectedId(state.selectedUsbId, state.usbDevices);
    pushLog(`USB list refreshed: ${state.usbDevices.length} device(s).`);
  });
}

async function connectUsb() {
  await runAction("Connecting USB printer...", async (plugin) => {
    if (!state.selectedUsbId) {
      throw new Error("Select a USB printer first.");
    }
    state.status = await plugin.connect({
      transport: "usb",
      deviceId: state.selectedUsbId,
      timeoutMs: 10000,
    });
  });
}

async function printDemoReceipt() {
  await runAction("Printing test receipt...", async (plugin) => {
    state.status = await plugin.getStatus();
    await printTestReceipt(plugin, state.status);
    pushLog(`Printed test receipt to ${describeDevice(state.status.device)}.`);
    setBanner(`Printed test receipt to ${describeDevice(state.status.device)}.`);
  });
}

async function disconnectPrinter() {
  await runAction("Disconnecting printer...", async (plugin) => {
    state.status = await plugin.disconnect();
    pushLog("Disconnect request completed.");
    setBanner("Printer disconnected.");
  });
}

async function refreshStatus() {
  await runAction("Refreshing status...", async (plugin) => {
    state.status = await plugin.getStatus();
    pushLog(`Status refreshed: ${state.status.connectionState || "disconnected"}.`);
  }, { silent: true });
}

async function runAction(message, action, options = {}) {
  const plugin = getPlugin();
  if (!plugin) {
    setBanner("Capacitor plugin not found.", true);
    return;
  }

  const { silent = false, clearBusyOnSuccess = true } = options;
  state.busy = message;
  if (!silent) {
    setBanner(message);
  }
  render();

  try {
    await action(plugin);
  } catch (error) {
    const messageText = readError(error);
    setBanner(messageText, true);
    pushLog(messageText, true);
    state.busy = "";
    render();
    return;
  }

  if (clearBusyOnSuccess) {
    state.busy = "";
  }
  render();
}

function render() {
  renderStatus();
  renderDeviceList(els.bleList, state.bleDevices, state.selectedBleId, "ble");
  renderDeviceList(els.usbList, state.usbDevices, state.selectedUsbId, "usb");
  renderLog();
  const busy = Boolean(state.busy);
  els.scanBle.disabled = busy || state.scanning;
  els.stopBle.disabled = busy || !state.scanning;
  els.connectBle.disabled = busy || !state.selectedBleId;
  els.listUsb.disabled = busy;
  els.connectUsb.disabled = busy || !state.selectedUsbId;
  els.printTest.disabled = busy || !state.status.connected;
  els.disconnect.disabled = busy || state.status.connectionState === "disconnected";
  els.refreshStatus.disabled = busy;
}

function renderStatus() {
  els.statusConnection.textContent = state.status.connectionState || "disconnected";
  els.statusTransport.textContent = (state.status.transport || "none").toUpperCase();
  els.statusPrinter.textContent = describeDevice(state.status.device);
  els.statusReconnect.textContent = state.status.reconnectMaxAttempts
    ? `${state.status.reconnectAttempt || 0}/${state.status.reconnectMaxAttempts}`
    : "n/a";
  const lastError = state.status.lastError?.message;
  els.statusError.hidden = !lastError;
  els.statusError.textContent = lastError || "";
}

function renderDeviceList(container, devices, selectedId, transport) {
  if (!container) {
    return;
  }
  if (!devices.length) {
    container.className = "device-list empty";
    container.textContent = `No ${transport.toUpperCase()} devices loaded yet.`;
    return;
  }

  container.className = "device-list";
  container.innerHTML = devices.map((device) => {
    const checked = device.id === selectedId ? "checked" : "";
    const selectedClass = device.id === selectedId ? " selected" : "";
    const extras = [
      device.rssi !== undefined ? `RSSI ${device.rssi}` : "",
      device.vendorId !== undefined ? `VID ${device.vendorId}` : "",
      device.productId !== undefined ? `PID ${device.productId}` : "",
    ].filter(Boolean).join(" | ");
    return `
      <div class="device${selectedClass}">
        <label>
          <span class="device-header">
            <input type="radio" name="${transport}-device" value="${escapeHtml(device.id)}" ${checked} />
            <span class="device-name">${escapeHtml(describeDevice(device))}</span>
          </span>
          <span class="device-meta">${escapeHtml(device.id)}</span>
          <span class="device-meta">${escapeHtml(extras || transport.toUpperCase())}</span>
        </label>
      </div>
    `;
  }).join("");

  container.querySelectorAll(`input[name="${transport}-device"]`).forEach((input) => {
    input.addEventListener("change", (event) => {
      const value = event.target.value;
      if (transport === "ble") {
        state.selectedBleId = value;
      } else {
        state.selectedUsbId = value;
      }
      render();
    });
  });
}

function renderLog() {
  if (!els.log) {
    return;
  }
  els.log.innerHTML = state.log.map((entry) => `
    <div class="log-entry${entry.error ? " error" : ""}">${escapeHtml(entry.message)}</div>
  `).join("");
}

function pushLog(message, error = false) {
  state.log.unshift({ message: `${timestamp()} ${message}`, error });
  state.log = state.log.slice(0, 24);
  renderLog();
}

function setBanner(message, error = false) {
  els.banner.textContent = message;
  els.banner.className = error ? "banner status-error" : "banner";
}

function keepSelectedId(currentId, devices) {
  if (currentId && devices.some((device) => device.id === currentId)) {
    return currentId;
  }
  return devices[0]?.id || "";
}

function upsertDevice(devices, nextDevice) {
  return [...devices.filter((device) => device.id !== nextDevice.id), nextDevice];
}

function getPlugin() {
  return window.Capacitor?.Plugins?.JenixThermalPrinter || null;
}

function readError(error) {
  if (error && typeof error === "object" && typeof error.message === "string") {
    return error.message;
  }
  return "Thermal printer request failed.";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function timestamp() {
  return new Date().toLocaleTimeString();
}
