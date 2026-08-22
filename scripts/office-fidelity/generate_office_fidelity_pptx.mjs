import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const output = process.argv[2];
const previewDir = process.argv[3];
if (!output || !previewDir) throw new Error("usage: node generate_office_fidelity_pptx.mjs OUTPUT.pptx PREVIEW_DIR");

async function saveBlob(path, blob) {
  await fs.writeFile(path, new Uint8Array(await blob.arrayBuffer()));
}

function textBox(slide, name, text, position, style) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = text;
  shape.text.style = style;
  return shape;
}

await fs.mkdir(previewDir, { recursive: true });
const presentation = Presentation.create({ slideSize: { width: 1280, height: 720 } });

const titleSlide = presentation.slides.add();
titleSlide.background.fill = "#F7F9FC";
textBox(
  titleSlide,
  "title",
  "PPTX 中文标题：离线转换保真度",
  { left: 88, top: 160, width: 1104, height: 110 },
  { fontSize: 68, bold: true, color: "#0B2545", alignment: "center" },
);
textBox(
  titleSlide,
  "subtitle",
  "PPTX-CJK-PASS · 中文字体、标点，English 2026",
  { left: 120, top: 300, width: 1040, height: 70 },
  { fontSize: 26, color: "#49627A", alignment: "center" },
);
textBox(
  titleSlide,
  "footer",
  "OpenConvert Office Fidelity Matrix · 01",
  { left: 88, top: 650, width: 1104, height: 28 },
  { fontSize: 14, color: "#6B7785", alignment: "right" },
);

const tableSlide = presentation.slides.add();
tableSlide.background.fill = "#FFFFFF";
textBox(
  tableSlide,
  "table-title",
  "PPTX 表格标记：区域交付概览",
  { left: 80, top: 54, width: 1120, height: 60 },
  { fontSize: 48, bold: true, color: "#0B2545" },
);
textBox(
  tableSlide,
  "table-lead",
  "PPTX-第二页-乙：关键中文与表格内容应保留",
  { left: 82, top: 122, width: 1000, height: 44 },
  { fontSize: 22, color: "#49627A" },
);
const table = tableSlide.tables.add({
  rows: 5,
  columns: 4,
  left: 90,
  top: 200,
  width: 1100,
  height: 330,
  columnTracks: [
    { mode: "fr", value: 1.2 },
    { mode: "fr", value: 1 },
    { mode: "fr", value: 1 },
    { mode: "fr", value: 1.4 },
  ],
  values: [
    ["区域", "任务数", "成功率", "验收状态"],
    ["华东", 128, "99.2%", "中文正常"],
    ["华南", 96, "98.9%", "表格正常"],
    ["西部", 74, "100%", "字体正常"],
    ["合计", 298, "99.3%", "PPTX-TABLE-PASS"],
  ],
});
table.styleOptions = { headerRow: true, bandedRows: true, firstColumn: true };
table.cells.block({ row: 0, column: 0, rowCount: 1, columnCount: 4 }).assign({
  fill: "#2E74B5",
  textStyle: { bold: true, color: "#FFFFFF", fontSize: 24 },
  anchor: "middle",
});
table.cells.block({ row: 1, column: 0, rowCount: 4, columnCount: 4 }).assign({
  textStyle: { color: "#26384A", fontSize: 24 },
  anchor: "middle",
  margins: { left: 12, right: 12, top: 8, bottom: 8 },
});
table.borders.assign({ style: "solid", fill: "#C9D3DE", width: 1 });
textBox(
  tableSlide,
  "footer",
  "OpenConvert Office Fidelity Matrix · 02",
  { left: 88, top: 650, width: 1104, height: 28 },
  { fontSize: 14, color: "#6B7785", alignment: "right" },
);

for (const [index, slide] of presentation.slides.items.entries()) {
  const png = await presentation.export({ slide, format: "png", scale: 1 });
  await saveBlob(`${previewDir}/slide-${index + 1}.png`, png);
  const layout = await slide.export({ format: "layout" });
  await fs.writeFile(`${previewDir}/slide-${index + 1}.layout.json`, await layout.text());
}
const snapshot = await presentation.inspect({ kind: "slide,textbox,table", maxChars: 8000 });
await fs.writeFile(`${previewDir}/inspect.ndjson`, snapshot.ndjson);
const deck = await PresentationFile.exportPptx(presentation);
await deck.save(output);
