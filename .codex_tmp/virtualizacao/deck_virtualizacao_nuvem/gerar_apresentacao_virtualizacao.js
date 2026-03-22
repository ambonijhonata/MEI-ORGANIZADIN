const fs = require("fs");
const path = require("path");
const PptxGenJS = require("pptxgenjs");
const {
  warnIfSlideHasOverlaps,
  warnIfSlideElementsOutOfBounds,
} = require("./pptxgenjs_helpers/layout");

const INPUT_TXT = path.resolve(
  __dirname,
  "../pesquisa_virtualizacao_extraida_utf8.txt"
);
const OUTPUT_PPTX = path.resolve(
  __dirname,
  "../../Apresentacao_Virtualizacao_Nuvem.pptx"
);

const SECTION_ORDER = [
  "Virtualização",
  "Contexto",
  "Problema resolvido pela virutalização",
  "Como funciona",
  "Benefícios",
];

function parseSections(rawText) {
  const sections = {};
  let current = null;
  const lines = rawText
    .replace(/\r/g, "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  for (const line of lines) {
    if (SECTION_ORDER.includes(line)) {
      current = line;
      if (!sections[current]) {
        sections[current] = [];
      }
      continue;
    }
    if (current) {
      sections[current].push(line);
    }
  }
  return sections;
}

function firstSentence(text) {
  const parts = text
    .split(/(?<=[.!?])\s+/)
    .map((p) => p.trim())
    .filter(Boolean);
  return parts[0] || text.trim();
}

function shorten(text, maxChars = 155) {
  const clean = text.replace(/\s+/g, " ").trim();
  if (clean.length <= maxChars) {
    return clean;
  }
  const slice = clean.slice(0, maxChars);
  const safeCut = slice.lastIndexOf(" ");
  const cut = safeCut > 0 ? slice.slice(0, safeCut) : slice;
  return `${cut.trim()}...`;
}

function unique(items) {
  return [...new Set(items)];
}

function validateSections(sections) {
  const missing = SECTION_ORDER.filter((name) => !sections[name]);
  if (missing.length > 0) {
    throw new Error(`Seções não encontradas no arquivo extraído: ${missing.join(", ")}`);
  }
}

function buildSlideBullets(sections) {
  const virtualizacao = sections["Virtualização"];
  const contexto = sections["Contexto"];
  const problema = sections["Problema resolvido pela virutalização"];
  const comoFunciona = sections["Como funciona"];
  const beneficios = sections["Benefícios"];

  const slide1 = unique(
    virtualizacao
      .slice(0, 4)
      .map(firstSentence)
      .map((item) => shorten(item, 150))
  );

  const slide2 = unique(
    contexto
      .slice(0, 5)
      .map(firstSentence)
      .map((item) => shorten(item, 145))
  );

  const slide3 = unique(
    problema
      .slice(0, 5)
      .map(firstSentence)
      .map((item) => shorten(item, 150))
  );

  const slide4 = unique(
    [0, 1, 2, 3, 5, 6]
      .filter((idx) => idx < comoFunciona.length)
      .map((idx) => firstSentence(comoFunciona[idx]))
      .map((item) => shorten(item, 150))
  );

  const slide5 = unique(
    beneficios
      .slice(0, 7)
      .map((item) => shorten(item, 145))
  );

  return {
    virtualizacao: slide1,
    contexto: slide2,
    problema: slide3,
    comoFunciona: slide4,
    beneficios: slide5,
  };
}

function addCloudBackground(slide) {
  slide.background = { color: "F5FAFF" };

  slide.addShape("rect", {
    x: 0,
    y: 0,
    w: 13.333,
    h: 0.78,
    fill: { color: "DBEEFF" },
    line: { color: "DBEEFF" },
  });

  slide.addShape("roundRect", {
    x: 11.0,
    y: 0.15,
    w: 1.8,
    h: 0.42,
    fill: { color: "CDE8FF", transparency: 10 },
    line: { color: "A6CFF4", pt: 1 },
  });
  slide.addText("Cloud", {
    x: 11.0,
    y: 0.215,
    w: 1.8,
    h: 0.2,
    fontFace: "Calibri",
    bold: true,
    color: "2E6C98",
    fontSize: 11,
    align: "center",
  });
}

function addTitle(slide, title) {
  slide.addText(title, {
    x: 0.85,
    y: 0.16,
    w: 8.8,
    h: 0.4,
    fontFace: "Calibri",
    bold: true,
    color: "0A446D",
    fontSize: 24,
  });

}

function addBulletsCard(slide, bullets) {
  slide.addShape("roundRect", {
    x: 0.7,
    y: 1.06,
    w: 11.95,
    h: 5.95,
    fill: { color: "FFFFFF", transparency: 3 },
    line: { color: "C9E3F9", pt: 1.25 },
  });

  const fontSize = bullets.length >= 7 ? 16 : bullets.length >= 6 ? 17 : 18;
  const runs = bullets.map((item, idx) => ({
    text: item,
    options: {
      bullet: { indent: 16 },
      breakLine: idx < bullets.length - 1,
    },
  }));

  slide.addText(runs, {
    x: 1.03,
    y: 1.38,
    w: 10.9,
    h: 5.25,
    fontFace: "Calibri",
    color: "193A55",
    valign: "top",
    fontSize,
    paraSpaceAfterPt: 8,
    lineSpacingMultiple: 1.1,
  });
}

function finalizeSlide(slide, pptx) {
  warnIfSlideHasOverlaps(slide, pptx, {
    muteContainment: true,
    ignoreLines: true,
    ignoreDecorativeShapes: true,
  });
  warnIfSlideElementsOutOfBounds(slide, pptx);
}

function buildDeck(content) {
  const pptx = new PptxGenJS();
  pptx.layout = "LAYOUT_WIDE";
  pptx.author = "Codex";
  pptx.subject = "Virtualização em Computação em Nuvem";
  pptx.title = "Virtualização - Aula de Computação em Nuvem";
  pptx.lang = "pt-BR";
  pptx.company = "Ciência da Computação";
  pptx.theme = {
    headFontFace: "Calibri",
    bodyFontFace: "Calibri",
    lang: "pt-BR",
  };

  const slide1 = pptx.addSlide();
  addCloudBackground(slide1);
  addTitle(slide1, "Virtualização");
  addBulletsCard(slide1, content.virtualizacao);
  finalizeSlide(slide1, pptx);

  const slide2 = pptx.addSlide();
  addCloudBackground(slide2);
  addTitle(slide2, "Contexto de surgimento");
  addBulletsCard(slide2, content.contexto);
  finalizeSlide(slide2, pptx);

  const slide3 = pptx.addSlide();
  addCloudBackground(slide3);
  addTitle(slide3, "Problema resolvido pela virutalização");
  addBulletsCard(slide3, content.problema);
  finalizeSlide(slide3, pptx);

  const slide4 = pptx.addSlide();
  addCloudBackground(slide4);
  addTitle(slide4, "Como funciona");
  addBulletsCard(slide4, content.comoFunciona);
  finalizeSlide(slide4, pptx);

  const slide5 = pptx.addSlide();
  addCloudBackground(slide5);
  addTitle(slide5, "Benefícios");
  addBulletsCard(slide5, content.beneficios);
  finalizeSlide(slide5, pptx);

  const slide6 = pptx.addSlide();
  addCloudBackground(slide6);
  addTitle(slide6, "Conclusão");
  finalizeSlide(slide6, pptx);

  return pptx;
}

async function main() {
  const raw = fs.readFileSync(INPUT_TXT, "utf8");
  const sections = parseSections(raw);
  validateSections(sections);
  const content = buildSlideBullets(sections);
  const pptx = buildDeck(content);
  await pptx.writeFile({ fileName: OUTPUT_PPTX });
  console.log(`Apresentação gerada em: ${OUTPUT_PPTX}`);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
