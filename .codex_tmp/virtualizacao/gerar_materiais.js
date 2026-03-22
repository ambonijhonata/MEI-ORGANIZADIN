const fs = require("fs");
const path = require("path");
const PptxGenJS = require("pptxgenjs");
const PDFDocument = require("pdfkit");

const outputDir = "C:/Users/Pichau/source/repos/TCC/API";
const outputPptx = path.join(outputDir, "Virtualizacao_Apresentacao.pptx");
const outputPdf = path.join(outputDir, "Virtualizacao_Apoio.pdf");

function bulletText(items) {
  return items.map((item) => `• ${item}`).join("\n");
}

function addHeader(slide, title) {
  slide.background = { color: "F7FAFC" };
  slide.addShape("rect", {
    x: 0,
    y: 0,
    w: 13.33,
    h: 0.8,
    fill: { color: "0E7490" },
    line: { color: "0E7490" },
  });
  slide.addText(title, {
    x: 0.5,
    y: 0.17,
    w: 12.5,
    h: 0.4,
    color: "FFFFFF",
    fontSize: 20,
    bold: true,
    fontFace: "Calibri",
  });
}

function addBulletsSlide(pptx, title, bullets, footer) {
  const slide = pptx.addSlide();
  addHeader(slide, title);
  slide.addText(bulletText(bullets), {
    x: 0.8,
    y: 1.1,
    w: 11.9,
    h: 5.6,
    fontSize: 22,
    color: "0F172A",
    valign: "top",
    breakLine: true,
    fontFace: "Calibri",
    lineSpacingMultiple: 1.2,
  });
  if (footer) {
    slide.addText(footer, {
      x: 0.8,
      y: 6.75,
      w: 12.0,
      h: 0.4,
      fontSize: 10,
      color: "475569",
      italic: true,
      fontFace: "Calibri",
    });
  }
}

function buildPresentation() {
  const pptx = new PptxGenJS();
  pptx.layout = "LAYOUT_WIDE";
  pptx.author = "Codex";
  pptx.company = "TCC - Computacao em Nuvem";
  pptx.subject = "Virtualizacao";
  pptx.title = "O que e Virtualizacao";
  pptx.lang = "pt-BR";

  const capa = pptx.addSlide();
  capa.background = { color: "0F172A" };
  capa.addShape("roundRect", {
    x: 0.8,
    y: 1.0,
    w: 11.7,
    h: 4.6,
    fill: { color: "FFFFFF", transparency: 5 },
    line: { color: "22D3EE", pt: 2 },
    radius: 0.08,
  });
  capa.addText("O QUE E VIRTUALIZACAO", {
    x: 1.2,
    y: 2.0,
    w: 11.0,
    h: 0.9,
    fontSize: 44,
    bold: true,
    color: "0F172A",
    align: "center",
    fontFace: "Calibri",
  });
  capa.addText("Fundamentos, arquitetura, tipos, seguranca e relacao com nuvem", {
    x: 1.2,
    y: 3.15,
    w: 11.0,
    h: 0.7,
    fontSize: 20,
    color: "155E75",
    align: "center",
    fontFace: "Calibri",
  });
  capa.addText("Material preparado com base no trabalho base + pesquisa tecnica aprofundada", {
    x: 1.2,
    y: 4.05,
    w: 11.0,
    h: 0.5,
    fontSize: 14,
    color: "334155",
    align: "center",
    fontFace: "Calibri",
  });

  addBulletsSlide(
    pptx,
    "Objetivos da Apresentacao",
    [
      "Conceituar virtualizacao de forma tecnica e didatica.",
      "Explicar como hipervisores, VMs e isolamento funcionam na pratica.",
      "Comparar tipos de virtualizacao e diferenciar VM de container.",
      "Relacionar virtualizacao com computacao em nuvem, eficiencia e seguranca.",
      "Apresentar riscos, boas praticas e tendencias atuais.",
    ],
    "Roteiro sugerido: 20 a 30 minutos."
  );

  addBulletsSlide(
    pptx,
    "Analise da Secao \"Virtualizacao\" do Trabalho Base",
    [
      "Pontos fortes: contexto historico correto e foco em eficiencia operacional.",
      "Acerto conceitual: virtualizacao como base para consolidacao de servidores.",
      "Boa conexao com nuvem: mostra relacao com provisionamento mais rapido.",
      "Linguagem clara para introducao academica.",
      "Conclusao: a secao e boa para iniciar, mas pode ser aprofundada tecnicamente.",
    ],
    "Base: secao localizada entre \"Virtualizacao (anos 2000)\" e \"Consolidacao dos Data Centers\"."
  );

  addBulletsSlide(
    pptx,
    "Oportunidades de Melhoria na Secao",
    [
      "Falta distinguir hipervisor tipo 1 (bare-metal) e tipo 2 (hosted).",
      "Nao detalha diferenca entre virtualizacao completa, para-virtualizacao e emulacao.",
      "Nao aborda virtualizacao de rede, armazenamento, aplicacao e desktop.",
      "Seguranca poderia incluir risco de comprometimento do hipervisor e superficie de ataque.",
      "Faltam exemplos atuais de uso: migracao ao vivo, isolamento de workloads e escalabilidade.",
    ],
    "Melhorias aumentam rigor tecnico e valor para banca/avaliacao."
  );

  addBulletsSlide(
    pptx,
    "O que e Virtualizacao?",
    [
      "Virtualizacao e a abstracao de recursos fisicos para criar ambientes logicos isolados.",
      "Uma unica maquina fisica pode executar varias maquinas virtuais (VMs).",
      "Cada VM possui sistema operacional, CPU, memoria, armazenamento e rede virtuais.",
      "O controle de recursos e feito pelo hipervisor (Virtual Machine Monitor).",
      "Resultado: melhor uso de hardware, flexibilidade operacional e isolamento entre cargas.",
    ],
    "NIST SP 800-125 e referencia classica para fundamentos de virtualizacao completa."
  );

  addBulletsSlide(
    pptx,
    "Como Funciona na Arquitetura",
    [
      "Camada fisica: CPU, RAM, disco e interfaces de rede.",
      "Hipervisor: particiona e agenda recursos para as VMs.",
      "VMs (guest OS): executam aplicacoes de forma isolada entre si.",
      "Plano de gerenciamento: criacao, monitoramento, snapshot, backup e migracao.",
      "Hardware assistido (ex.: Intel VT-x e AMD-V) reduz overhead e melhora seguranca.",
    ],
    "Arquitetura semelhante em diferentes plataformas (KVM, Hyper-V, VMware, Xen)."
  );

  addBulletsSlide(
    pptx,
    "Tipos de Hipervisor",
    [
      "Tipo 1 (bare-metal): roda direto no hardware; comum em data center.",
      "Tipo 2 (hosted): roda sobre um sistema operacional hospedeiro.",
      "Tipo 1 tende a oferecer melhor desempenho e isolamento para producao.",
      "Tipo 2 e muito usado para laboratorio, estudo e desenvolvimento local.",
      "Exemplos: KVM/Hyper-V/vSphere (tipo 1), Workstation/VirtualBox (tipo 2).",
    ],
    "Conceitos alinhados com documentacao da Red Hat e Microsoft Learn."
  );

  addBulletsSlide(
    pptx,
    "Principais Tipos de Virtualizacao",
    [
      "Servidor: multiplas VMs em um host fisico.",
      "Desktop: ambientes de usuario centralizados e padronizados.",
      "Armazenamento: pools logicos sobre varios dispositivos fisicos.",
      "Aplicacao: execucao desacoplada do OS original.",
      "Rede/NFV e dados: funcoes e fontes distribuidas tratadas como servico/logica unica.",
    ],
    "A escolha do tipo depende do problema de negocio e do requisito tecnico."
  );

  addBulletsSlide(
    pptx,
    "Virtualizacao x Containers",
    [
      "VM inclui sistema operacional completo e seu proprio kernel.",
      "Container isola processos e compartilha o kernel do host.",
      "Containers sao mais leves e escalam rapido para microsservicos.",
      "VMs oferecem isolamento forte para cargas heterogeneas e legadas.",
      "Ambientes modernos combinam os dois modelos (VM + container).",
    ],
    "Docker Docs: containers compartilham kernel; VMs carregam OS completo."
  );

  addBulletsSlide(
    pptx,
    "Beneficios Reais",
    [
      "Consolidacao de servidores e maior taxa de utilizacao de hardware.",
      "Provisionamento rapido e padronizado de ambientes.",
      "Reducao de custos com energia, espaco e operacao.",
      "Facilidade para backup, snapshot, clonagem e recuperacao.",
      "Maior agilidade para testes, homologacao e continuidade do negocio.",
    ],
    "Beneficios variam conforme maturidade operacional e governanca."
  );

  addBulletsSlide(
    pptx,
    "Riscos e Limitacoes",
    [
      "Comprometimento do hipervisor pode afetar varias VMs ao mesmo tempo.",
      "Concentracao de workloads aumenta impacto de falhas fisicas.",
      "Contencao de recursos gera degradacao de desempenho se mal dimensionado.",
      "Gestao de imagens e snapshots sem governanca aumenta risco de exposicao.",
      "Migrao sem planejamento pode gerar dependencia tecnica e custo oculto.",
    ],
    "Virtualizacao melhora eficiencia, mas exige operacao madura."
  );

  addBulletsSlide(
    pptx,
    "Boas Praticas de Seguranca",
    [
      "Aplicar hardening em hipervisor, host, rede virtual e guests.",
      "Restringir e proteger interfaces administrativas (MFA, rede segregada).",
      "Atualizar componentes e aplicar correcoes de seguranca continuamente.",
      "Desabilitar recursos desnecessarios (compartilhamentos, dispositivos ociosos).",
      "Monitorar logs em todas as camadas e testar controles periodicamente.",
    ],
    "Diretrizes alinhadas ao NIST SP 800-125."
  );

  addBulletsSlide(
    pptx,
    "Virtualizacao e Computacao em Nuvem",
    [
      "Virtualizacao habilita o pool compartilhado de recursos da nuvem.",
      "Permite provisionar e liberar capacidade com rapidez e automacao.",
      "Suporta modelos IaaS e parte da base de PaaS.",
      "Combinada com orquestracao, viabiliza elasticidade e escalabilidade.",
      "Sem virtualizacao, a nuvem perde eficiencia operacional e densidade.",
    ],
    "NIST SP 800-145 define a nuvem como acesso sob demanda a recursos compartilhados."
  );

  addBulletsSlide(
    pptx,
    "Cenarios de Uso",
    [
      "Data center corporativo: consolidacao de ERP, banco e middleware.",
      "Universidades/laboratorios: ambientes de pratica isolados por turma.",
      "Dev/Test: reproducao de ambientes e testes de regressao rapidos.",
      "Telecom (NFV): funcoes de rede virtualizadas em hardware comum.",
      "Plano de continuidade: replicacao e recuperacao de VMs em outro site.",
    ],
    "Use cases variam entre desempenho, custo, compliance e disponibilidade."
  );

  addBulletsSlide(
    pptx,
    "Tendencias",
    [
      "Nested virtualization para laboratorios e pipelines de plataforma.",
      "Confidential VMs com criptografia em uso para dados sensiveis.",
      "Convergencia VM + Kubernetes (ex.: KubeVirt/OpenShift Virtualization).",
      "Automacao por politica (IaC, observabilidade e remediacao automatica).",
      "Foco crescente em eficiencia energetica e sustentabilidade da infraestrutura.",
    ],
    "A virtualizacao segue central para plataformas hibridas e multicloud."
  );

  addBulletsSlide(
    pptx,
    "Conclusao",
    [
      "Virtualizacao nao e apenas consolidacao: e uma camada de abstracao estrategica.",
      "Ela melhora uso de recursos, padroniza operacoes e acelera entrega de TI.",
      "O valor depende de arquitetura correta, seguranca e governanca operacional.",
      "Em conjunto com nuvem e containers, sustenta ambientes modernos e resilientes.",
      "Mensagem final: virtualizar com criterio tecnico gera escala com controle.",
    ],
    "Fim."
  );

  const refs = pptx.addSlide();
  addHeader(refs, "Referencias Tecnicas (selecao)");
  refs.addText(
    bulletText([
      "NIST SP 800-125 - Guide to Security for Full Virtualization Technologies.",
      "NIST SP 800-145 - The NIST Definition of Cloud Computing.",
      "Microsoft Learn - Hyper-V overview e arquitetura.",
      "Docker Docs - Containers versus virtual machines.",
      "Red Hat Docs - Migrating virtual machines (RHEL 9).",
      "Red Hat - What is virtualization? / What is a hypervisor?",
      "Intel SDM - VMX e Intel VT.",
      "IBM z/VM history - VM/370 e origem historica.",
      "QEMU project - KVM/Xen com desempenho proximo ao nativo.",
    ]),
    {
      x: 0.8,
      y: 1.1,
      w: 11.9,
      h: 5.8,
      fontSize: 16,
      color: "0F172A",
      valign: "top",
      breakLine: true,
      fontFace: "Calibri",
      lineSpacingMultiple: 1.15,
    }
  );

  return pptx.writeFile({ fileName: outputPptx });
}

function wrapText(doc, text, width) {
  doc.text(text, {
    width,
    align: "left",
    lineGap: 2,
  });
  doc.moveDown(0.55);
}

function sectionTitle(doc, text) {
  doc.font("Helvetica-Bold").fontSize(15).fillColor("#0E7490").text(text);
  doc.moveDown(0.25);
}

function ensureSpace(doc, needed = 120) {
  if (doc.y > doc.page.height - doc.page.margins.bottom - needed) {
    doc.addPage();
  }
}

function buildSupportPdf() {
  const doc = new PDFDocument({
    size: "A4",
    margin: 48,
    info: {
      Title: "Virtualizacao - Material de Apoio",
      Author: "Codex",
      Subject: "Fundamentos de virtualizacao",
    },
  });
  const out = fs.createWriteStream(outputPdf);
  doc.pipe(out);

  doc.rect(0, 0, doc.page.width, 95).fill("#0F172A");
  doc.fillColor("#FFFFFF").font("Helvetica-Bold").fontSize(24).text("VIRTUALIZACAO", 48, 28);
  doc.font("Helvetica").fontSize(12).text("Material de apoio para apresentacao", 48, 62);
  doc.fillColor("#0F172A");
  doc.moveDown(5);
  doc.font("Helvetica").fontSize(10).fillColor("#334155").text(`Gerado em: ${new Date().toLocaleString("pt-BR")}`);
  doc.moveDown(0.8);
  doc.font("Helvetica").fontSize(11).fillColor("#0F172A").text(
    "Este material foi estruturado em duas bases: (1) analise da secao \"Virtualizacao\" do trabalho original enviado e (2) pesquisa tecnica aprofundada em fontes primarias."
  );
  doc.addPage();

  sectionTitle(doc, "1) Analise da secao \"Virtualizacao\" do trabalho base");
  wrapText(
    doc,
    "Trecho analisado (resumo): a secao apresenta a virtualizacao como resposta ao baixo aproveitamento de servidores fisicos, destacando execucao de multiplas maquinas virtuais em um unico host, isolamento de sistemas, reducao de custos e agilidade no provisionamento."
  );
  wrapText(
    doc,
    "Pontos fortes: a narrativa historica esta correta para uma introducao academica, com boa conexao entre virtualizacao, consolidacao de infraestrutura e computacao em nuvem. O texto tambem reforca beneficios concretos que fazem sentido em ambientes corporativos."
  );
  wrapText(
    doc,
    "Lacunas tecnicas: faltam classificacoes fundamentais (hipervisor tipo 1 x tipo 2; virtualizacao completa x emulacao), nao ha detalhamento de camadas arquiteturais (host, hipervisor, guests, plano de controle), e a parte de seguranca ainda esta superficial para cenarios de producao."
  );
  wrapText(
    doc,
    "Recomendacao de melhoria para banca/apresentacao: manter o texto atual como introducao e acrescentar, em seguida, um bloco tecnico curto com tipos, arquitetura, riscos e boas praticas. Isso eleva rigor sem perder clareza."
  );

  sectionTitle(doc, "2) O que e virtualizacao (fundamento tecnico)");
  wrapText(
    doc,
    "Virtualizacao e a abstracao de recursos de computacao para criar ambientes logicos isolados sobre a mesma base fisica. Na virtualizacao completa, um ou mais sistemas operacionais convidados executam sobre hardware virtualizado controlado por um hipervisor."
  );
  wrapText(
    doc,
    "Em termos praticos, a virtualizacao transforma um servidor fisico em varios ambientes independentes, cada um com CPU, memoria, disco e rede virtuais. Isso permite consolidacao de cargas, padronizacao operacional e melhor uso de capacidade."
  );

  sectionTitle(doc, "3) Como funciona a arquitetura");
  wrapText(
    doc,
    "A pilha classica possui: (a) hardware fisico, (b) hipervisor, (c) VMs com sistemas convidados, e (d) plano de gerenciamento. O hipervisor intermedia instrucoes e acesso a recursos fisicos, impondo isolamento e politicas de alocacao."
  );
  wrapText(
    doc,
    "Extensoes de hardware como Intel VT-x e AMD-V permitem que essa camada execute com desempenho proximo ao nativo em muitos cenarios, alem de melhorar controles de isolamento."
  );

  sectionTitle(doc, "4) Tipos de hipervisor");
  wrapText(
    doc,
    "Tipo 1 (bare-metal): executa diretamente no hardware e e padrao em data centers. Tipo 2 (hosted): executa sobre um sistema operacional hospedeiro e e comum em laboratorio e desktop."
  );
  wrapText(
    doc,
    "Em geral, tipo 1 favorece desempenho, isolamento e operacao empresarial; tipo 2 favorece simplicidade para aprendizado, testes e uso local."
  );

  ensureSpace(doc, 160);
  sectionTitle(doc, "5) Tipos de virtualizacao alem de servidores");
  wrapText(
    doc,
    "Desktop virtualization: centraliza ambientes de usuario e facilita governanca. Storage virtualization: agrega armazenamento em um pool logico. Application virtualization: desacopla app do OS original. NFV e virtualizacao de dados: desacoplam funcoes e fontes para maior flexibilidade operacional."
  );

  sectionTitle(doc, "6) VM x container");
  wrapText(
    doc,
    "VMs virtualizam hardware e carregam sistema operacional completo. Containers virtualizam no nivel do sistema operacional e compartilham o kernel do host. Containers costumam ser mais leves e rapidos para escalar; VMs tendem a oferecer maior isolamento de ambiente e compatibilidade com legados."
  );
  wrapText(
    doc,
    "Na pratica moderna, os dois coexistem: VMs no plano de infraestrutura e containers no plano de aplicacao."
  );

  sectionTitle(doc, "7) Beneficios para negocio e operacao");
  wrapText(
    doc,
    "Principais ganhos: consolidacao de servidores, melhor utilizacao de hardware, reducao de custos de energia/espaco, provisionamento rapido, clonagem e snapshot para agilidade, e suporte a continuidade de negocio."
  );
  wrapText(
    doc,
    "Tambem ha ganho pedagogico e de engenharia: ambientes de teste reproduziveis, laboratorios isolados e capacidade de rollback."
  );

  sectionTitle(doc, "8) Riscos e limitacoes");
  wrapText(
    doc,
    "Virtualizar concentra cargas em menos hosts. Se houver falha fisica ou comprometimento de hipervisor, o impacto pode ser amplo. Outro risco comum e contencao de recursos por superalocacao sem planejamento."
  );
  wrapText(
    doc,
    "A superficie de ataque inclui interfaces administrativas, imagens desatualizadas, snapshots esquecidos e configuracoes inseguras de rede virtual."
  );

  ensureSpace(doc, 180);
  sectionTitle(doc, "9) Seguranca e boas praticas (resumo operacional)");
  wrapText(
    doc,
    "1. Proteger todos os componentes (hipervisor, host, guests, storage e rede virtual).\n2. Restringir e auditar acesso administrativo.\n3. Aplicar patching e baselines de hardening.\n4. Desabilitar funcoes desnecessarias (ex.: compartilhamentos inseguros).\n5. Monitorar logs e eventos em todas as camadas.\n6. Planejar seguranca desde o desenho da solucao."
  );
  wrapText(
    doc,
    "Esses principios estao alinhados com as recomendacoes do NIST para virtualizacao completa."
  );

  sectionTitle(doc, "10) Relacao direta com computacao em nuvem");
  wrapText(
    doc,
    "A nuvem depende de recursos compartilhados, provisionamento rapido e elasticidade. A virtualizacao e uma tecnologia central para viabilizar esse modelo com isolamento, multiprovisionamento e controle de capacidade."
  );
  wrapText(
    doc,
    "Em IaaS, o recurso entregue ao cliente costuma ser uma VM com isolamento e politicas definidas no hipervisor/plataforma."
  );

  sectionTitle(doc, "11) Roteiro de fala sugerido para a apresentacao");
  wrapText(
    doc,
    "Abertura (2 min): conceito e importancia.\nFundamentos (8 min): arquitetura, tipos, VM x container.\nAplicacao (8 min): beneficios, riscos, seguranca e nuvem.\nFechamento (2 min): sintese e mensagem final."
  );

  doc.addPage();
  sectionTitle(doc, "Referencias principais consultadas");
  const refs = [
    "NIST SP 800-125 (PDF): https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-125.pdf",
    "NIST SP 800-145: https://csrc.nist.gov/pubs/sp/800/145/final",
    "Microsoft Learn - Hyper-V overview: https://learn.microsoft.com/en-us/windows-server/virtualization/hyper-v/overview",
    "Microsoft Learn - Hyper-V architecture: https://learn.microsoft.com/en-us/windows-server/virtualization/hyper-v/architecture",
    "Microsoft Learn - Nested virtualization: https://learn.microsoft.com/en-us/windows-server/virtualization/hyper-v/nested-virtualization",
    "Docker Docs - What is a container?: https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/",
    "Red Hat - What is a hypervisor?: https://www.redhat.com/en/topics/virtualization/what-is-a-hypervisor",
    "Red Hat - What is virtualization?: https://www.redhat.com/en/topics/virtualization/what-is-virtualization",
    "Red Hat Docs - Migrating VMs (RHEL 9): https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/configuring_and_managing_virtualization/migrating-virtual-machines_configuring-and-managing-virtualization",
    "Intel SDM: https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html",
    "IBM z/VM history (50 years): https://www.ibm.com/support/pages/zvm/history/50th/index.html",
    "QEMU project: https://www.qemu.org/index.html",
  ];
  doc.font("Helvetica").fontSize(10.5).fillColor("#111827");
  refs.forEach((r, i) => {
    wrapText(doc, `${i + 1}. ${r}`);
  });
  doc.moveDown(0.8);
  doc.font("Helvetica-Oblique").fontSize(9.5).fillColor("#475569").text(
    "Observacao: fontes acessadas em 18/03/2026 para montagem do material."
  );

  doc.end();
  return new Promise((resolve, reject) => {
    out.on("finish", resolve);
    out.on("error", reject);
  });
}

async function main() {
  await buildPresentation();
  await buildSupportPdf();
  console.log(`PPTX: ${outputPptx}`);
  console.log(`PDF: ${outputPdf}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
