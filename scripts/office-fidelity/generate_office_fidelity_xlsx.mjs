import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const output = process.argv[2];
const previewDir = process.argv[3];
if (!output || !previewDir) throw new Error("usage: node generate_office_fidelity_xlsx.mjs OUTPUT.xlsx PREVIEW_DIR");

await fs.mkdir(previewDir, { recursive: true });
const workbook = Workbook.create();

function styleSheet(sheet, marker, rows) {
  sheet.showGridLines = false;
  sheet.getRange("A1:D1").merge();
  sheet.getRange("A1").values = [[marker]];
  sheet.getRange("A1:D1").format = {
    fill: "#0B2545",
    font: { bold: true, color: "#FFFFFF", size: 18 },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };
  sheet.getRange("A1:D1").format.rowHeight = 34;
  sheet.getRange("A3:D6").values = [
    ["区域", "一季度", "二季度", "合计"],
    ...rows,
  ];
  sheet.getRange("D4").formulas = [["=B4+C4"]];
  sheet.getRange("D4:D6").fillDown();
  sheet.getRange("A3:D3").format = {
    fill: "#2E74B5",
    font: { bold: true, color: "#FFFFFF" },
    horizontalAlignment: "center",
  };
  sheet.getRange("A4:A6").format.font = { bold: true, color: "#0B2545" };
  sheet.getRange("B4:D6").format.numberFormat = "¥#,##0";
  sheet.getRange("A3:D6").format.borders = { preset: "all", style: "thin", color: "#C9D3DE" };
  sheet.getRange("A3:D6").format.verticalAlignment = "center";
  sheet.getRange("A1:D6").format.wrapText = true;
  sheet.getRange("A1").format.columnWidth = 22;
  sheet.getRange("B1").format.columnWidth = 18;
  sheet.getRange("C1").format.columnWidth = 18;
  sheet.getRange("D1").format.columnWidth = 20;
  sheet.getRange("A3:D6").format.rowHeight = 26;
}

const summary = workbook.worksheets.add("数据总览");
styleSheet(summary, "XLSX-总览-甲：多工作表中文保真度", [
  ["华东", 1280000, 1460000, null],
  ["华南", 980000, 1120000, null],
  ["西部", 760000, 830000, null],
]);
summary.getRange("A8").values = [["XLSX-FORMULA-PASS"]];
summary.getRange("A8:D8").format = { fill: "#E8EEF5", font: { bold: true, color: "#0B2545" } };

const east = workbook.worksheets.add("华东区域");
styleSheet(east, "XLSX-华东-乙：上海、江苏、浙江", [
  ["上海", 520000, 610000, null],
  ["江苏", 430000, 480000, null],
  ["浙江", 330000, 370000, null],
]);

const south = workbook.worksheets.add("华南区域");
styleSheet(south, "XLSX-华南-丙：广东、福建、海南", [
  ["广东", 560000, 650000, null],
  ["福建", 280000, 320000, null],
  ["海南", 140000, 150000, null],
]);
south.getRange("A8").values = [["XLSX-SHEETS-PASS"]];
south.getRange("A8:D8").format = { fill: "#E8EEF5", font: { bold: true, color: "#0B2545" } };

const inspection = await workbook.inspect({
  kind: "sheet,formula,region",
  maxChars: 12000,
  tableMaxRows: 12,
  tableMaxCols: 6,
});
await fs.writeFile(`${previewDir}/inspect.ndjson`, inspection.ndjson);
const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "office fidelity formula error scan",
});
await fs.writeFile(`${previewDir}/formula-errors.ndjson`, errors.ndjson);

for (const sheetName of ["数据总览", "华东区域", "华南区域"]) {
  const preview = await workbook.render({ sheetName, range: "A1:D8", scale: 2, format: "png" });
  await fs.writeFile(`${previewDir}/${sheetName}.png`, new Uint8Array(await preview.arrayBuffer()));
}
const file = await SpreadsheetFile.exportXlsx(workbook);
await file.save(output);

// Re-import the exact final bytes and render again; this catches export-only drift.
const exported = await SpreadsheetFile.importXlsx(await FileBlob.load(output));
for (const sheetName of ["数据总览", "华东区域", "华南区域"]) {
  const preview = await exported.render({ sheetName, range: "A1:D8", scale: 2, format: "png" });
  await fs.writeFile(`${previewDir}/final-${sheetName}.png`, new Uint8Array(await preview.arrayBuffer()));
}
