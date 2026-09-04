export function describeDevice(device) {
  if (!device) {
    return "Unknown printer";
  }

  const name = typeof device.name === "string" ? device.name.trim() : "";
  return name || device.id || "Unknown printer";
}

export async function printTestReceipt(plugin, status) {
  if (!plugin) {
    throw new Error("Capacitor plugin is not available.");
  }
  if (!status || !status.connected) {
    throw new Error("Connect a printer before printing.");
  }

  const now = new Date();
  const transport = (status.transport || "unknown").toUpperCase();
  const printedAt = formatTimestamp(now);
  const qrData = `JENIX|${transport}|${now.toISOString()}`;
  const barcodeData = `TEST-${printedAt.replace(/[^0-9]/g, "").slice(0, 14)}`;

  await plugin.write({ data: [0x1b, 0x40] });
  await plugin.printText({ text: "JENIX INDIA PVT LTD\n", alignment: "center" });
  await plugin.printText({ text: "THERMAL PRINTER TEST\n\n", alignment: "center" });
  await plugin.printText({ text: `Transport: ${transport}\n` });
  await plugin.printText({ text: `Status: ${status.connectionState || "connected"}\n` });
  await plugin.printText({ text: `Printer: ${describeDevice(status.device)}\n` });
  await plugin.printText({ text: "-------------------------------\n" });
  await plugin.printText({ text: "Hello Jenix\n" });
  await plugin.printText({ text: `Printed: ${printedAt}\n` });
  await plugin.printText({ text: "QR test below\n\n" });
  await plugin.printQRCode({ data: qrData, alignment: "center", size: 6 });
  await plugin.feed({ lines: 1 });
  await plugin.printBarcode({
    data: barcodeData,
    format: "code128",
    alignment: "center",
    width: 3,
    height: 80,
  });
  await plugin.feed({ lines: 3 });
  await plugin.cut();
}

function formatTimestamp(value) {
  const year = value.getFullYear();
  const month = `${value.getMonth() + 1}`.padStart(2, "0");
  const day = `${value.getDate()}`.padStart(2, "0");
  const hours = `${value.getHours()}`.padStart(2, "0");
  const minutes = `${value.getMinutes()}`.padStart(2, "0");
  const seconds = `${value.getSeconds()}`.padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
