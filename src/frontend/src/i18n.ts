export type Locale = "pt-BR" | "en-US";

let currentLocale: Locale = "pt-BR";

const messages: Record<string, string> = {
  "Trecho analisado": "Analyzed excerpt",
  "Carregando aplicação": "Loading application",
  "FISAST · PLATAFORMA SAST": "FISAST · SAST PLATFORM",
  "Análise em andamento": "Analysis in progress",
  "Analisando os arquivos Java do repositório.": "Analyzing the repository's Java files.",
  "Isso pode levar alguns segundos.": "This may take a few seconds.",
  "Analisando…": "Analyzing…",
  "SEGURANÇA COMEÇA NO CÓDIGO": "SECURITY STARTS IN CODE",
  "Encontre riscos.": "Find risks.",
  "Construa com": "Build with",
  "confiança.": "confidence.",
  "Uma visão clara das vulnerabilidades do seu projeto, antes de elas se tornarem um problema.": "A clear view of your project's vulnerabilities before they become a problem.",
  "Análise estática · Java": "Static analysis · Java",
  "Plataforma SAST": "SAST Platform",
  "Crie sua conta": "Create your account",
  "Bem-vindo de volta": "Welcome back",
  "Cadastre-se para analisar seus repositórios.": "Sign up to analyze your repositories.",
  "Entre para analisar seus repositórios.": "Sign in to analyze your repositories.",
  "Senha": "Password",
  "Sua senha": "Your password",
  "Mostrar senha": "Show password",
  "Ocultar senha": "Hide password",
  "Confirmar senha": "Confirm password",
  "Repita sua senha": "Repeat your password",
  "As senhas não conferem": "Passwords do not match",
  "Mostrar confirmação de senha": "Show password confirmation",
  "Ocultar confirmação de senha": "Hide password confirmation",
  "Cadastrando…": "Registering…",
  "Entrando…": "Signing in…",
  "Criar conta": "Create account",
  "Entrar": "Sign in",
  "Já possui uma conta?": "Already have an account?",
  "Ainda não possui uma conta?": "Don't have an account yet?",
  "Criar cadastro": "Sign up",
  "PLATAFORMA DE ANÁLISE ESTÁTICA": "STATIC ANALYSIS PLATFORM",
  "SEU PRÓXIMO PASSO": "YOUR NEXT STEP",
  "Conheça os riscos de segurança presentes no seu código.": "Discover security risks in your code.",
  "Configure a análise": "Configure the analysis",
  "Informe a URL do repositório que deseja analisar.": "Enter the URL of the repository you want to analyze.",
  "Referência": "Reference",
  "opcional": "optional",
  "Branch, tag ou SHA": "Branch, tag or SHA",
  "Em branco, usamos o branch padrão.": "Leave blank to use the default branch.",
  "Somente repositórios públicos": "Public repositories only",
  "Iniciar análise": "Start analysis",
  "O que verificamos": "What we check",
  "Três verificações essenciais para começar.": "Three essential checks to get started.",
  "Credenciais no código": "Credentials in code",
  "Execução de comandos": "Command execution",
  "Desserialização insegura": "Unsafe deserialization",
  "ANÁLISE NÃO CONCLUÍDA": "ANALYSIS NOT COMPLETED",
  "Ocorreu um erro ao analisar": "An error occurred during analysis",
  "Tentar nova análise": "Try a new analysis",
  "Ver detalhes": "View details",
  "Fechar": "Close",
  "Cadastre um sistema primeiro": "Register a system first",
  "Cadastre um sistema para acompanhar o processamento e identificar vulnerabilidades no Dashboard.": "Register a system to monitor progress and identify vulnerabilities on the dashboard.",
  "Cadastrar novo": "Register new",
  "SEUS SISTEMAS": "YOUR SYSTEMS",
  "Selecione um sistema para acompanhar suas análises.": "Select a system to monitor its analyses.",
  "Sistemas": "Systems",
  "repositórios": "repositories",
  "Análises": "Analyses",
  "execuções consideradas": "runs included",
  "Vulnerabilidades/Melhorias": "Vulnerabilities/Improvements",
  "em todas as execuções": "across all runs",
  "Críticas": "Critical",
  "prioridade imediata": "immediate priority",
  "Crítica": "Critical",
  "Alta": "High",
  "Média": "Medium",
  "Baixa": "Low",
  "Resultados": "Results",
  "itens nas verificações": "items in checks",
  "Findings determinísticos prioritários": "Priority deterministic findings",
  "Nenhum item disponível para ordenar por prioridade.": "No items available to rank by priority.",
  "Arquivos críticos": "Critical files",
  "Nenhum finding determinístico.": "No deterministic findings.",
  "Sugestões consultivas": "Consultive suggestions",
  "Não alteram o ranking dos findings determinísticos.": "They do not affect deterministic findings ranking.",
  "Histórico de aplicações": "Application history",
  "SISTEMA": "SYSTEM",
  "Última análise": "Latest analysis",
  "Em processamento": "Processing",
  "Falha no processamento": "Processing failed",
  "Branch padrão": "Default branch",
  "Maior prioridade": "Highest priority",
  "Sem prioridade": "No priority",
  "Anteriores": "Previous",
  "Próximos": "Next",
  "Página": "Page",
  "Histórico de análises": "Analysis history",
  "Histórico de execuções": "Run history",
  "RELATÓRIO ANALÍTICO": "ANALYTICAL REPORT",
  "Tendência de vulnerabilidades": "Vulnerability trend",
  "Somente execuções concluídas e distintas": "Only completed, distinct runs",
  "Data": "Date",
  "Referência / SHA": "Reference / SHA",
  "Total": "Total",
  "Críticas determinísticas": "Deterministic critical",
  "Variação": "Change",
  "Cobertura": "Coverage",
  "Recomendações": "Recommendations",
  "Nenhuma execução anterior disponível para comparação.": "No previous run available for comparison.",
  "Atualizando os dados da análise selecionada…": "Updating selected analysis data…",
  "Não comparável": "Not comparable",
  "Estável": "Stable",
  "Atribuição": "Assignment",
  "Concatenação de string": "String concatenation",
  "Expressão condicional": "Conditional expression",
  "Engine de taint versão": "Taint engine version",
  "Avaliação consultiva da IA": "Consultive AI assessment",
  "ANÁLISE SEMÂNTICA": "SEMANTIC ANALYSIS",
  "Avaliação consultiva": "Consultive assessment",
  "Avaliação da IA": "AI assessment",
  "Confiança do modelo": "Model confidence",
  "Remediação sugerida": "Suggested remediation",
  "Limitações": "Limitations",
  "Sugestão consultiva": "Consultive suggestion",
  "Em correção": "In progress",
  "Aberta": "Open",
  "Corrigida": "Fixed",
  "Validada": "Validated",
  "Falso positivo": "False positive",
  "Risco aceito": "Risk accepted",
  "Observação / justificativa": "Note / justification",
  "Salvando…": "Saving…",
  "Registrar alteração": "Save change",
  "Análise": "Analysis",
  "RESULTADOS DA ANÁLISE": "ANALYSIS RESULTS",
  "Resultado parcial": "Partial result",
  "Visualização dos resultados": "Results view",
  "Vulnerabilidades e segurança": "Vulnerabilities and security",
  "Desempenho e recomendações": "Performance and recommendations",
  "Vulnerabilidades": "Vulnerabilities",
  "Recomendações de desempenho": "Performance recommendations",
  "Análise sem conclusão": "Incomplete analysis",
  "Aguardando resultados": "Waiting for results",
  "Nenhum item disponível nesta etapa parcial": "No items available at this partial stage",
  "Nenhuma vulnerabilidade ou melhoria encontrada": "No vulnerability or improvement found",
  "Descrição": "Description",
  "Regra": "Rule",
  "Linha": "Line",
  "coluna": "column",
  "Consultiva": "Consultive",
  "Severidade sugerida": "Suggested severity",
  "Provável falso positivo": "Likely false positive",
  "Sim": "Yes",
  "Não": "No",
  "Risco contextual": "Contextual risk",
  "Justificativa": "Rationale",
  "Evidências": "Evidence",
  "Ações recomendadas": "Recommended actions",
  "Concluída em": "Completed on",
  "Análise estática Java": "Java static analysis",
  "Repositório público do GitHub": "Public GitHub repository",
  "Linguagem": "Language",
  "Análise em andamento. Você pode continuar nesta página.": "Analysis in progress. You can stay on this page.",
  "Seu código é inspecionado sem ser executado. Apenas os resultados e trechos dos achados são salvos.": "Your code is inspected without execution. Only results and relevant excerpts are saved.",
  "Não foi possível acessar o repositório. Verifique se a URL está correta e se o repositório é público.": "Could not access the repository. Check its URL and ensure it is public.",
  "Não foi possível analisar os arquivos Java deste repositório. Tente novamente em instantes.": "Could not analyze this repository's Java files. Try again shortly.",
  "A análise foi interrompida ao preparar as informações complementares. Tente novamente em instantes.": "Analysis stopped while preparing additional information. Try again shortly.",
  "Ocorreu um erro inesperado durante a análise. Tente novamente em instantes.": "An unexpected analysis error occurred. Try again shortly.",
  "Métodos candidatos avaliados. A varredura consultiva não cobre todo o repositório.": "Candidate methods assessed. The consultive scan does not cover the whole repository.",
  "Cobertura parcial ou IA indisponível. Pode haver melhorias ainda não identificadas.": "Partial coverage or AI unavailable. Other improvements may remain unidentified.",
  "Nenhum método candidato às sugestões foi selecionado. Isso não significa ausência de melhorias.": "No candidate method was selected for suggestions. This does not mean there are no improvements.",
  "Sugestões consultivas em processamento. Os números ainda são parciais.": "Consultive suggestions are processing. Counts remain partial.",
  "Cobertura consultiva não informada nesta execução.": "Consultive coverage was not reported for this run.",
  "A análise falhou; os dados parciais não confirmam ausência de itens.": "Analysis failed; partial data does not confirm the absence of issues.",
  "Verificações em processamento; os totais podem mudar.": "Checks are processing; totals may change.",
  "Nenhum item disponível nas etapas parciais; isso não confirma ausência de problemas.": "No items in partial stages; this does not confirm the absence of problems.",
  "Nenhuma vulnerabilidade ou melhoria encontrada nesta execução.": "No vulnerability or improvement found in this run.",
  "Os indicadores de itens correspondem à execução selecionada. A prioridade usa a severidade de cada item.": "Item metrics refer to the selected run. Priority follows each item's severity.",
  "Execuções parciais ou com falha não são convertidas em zero: a ausência de findings só é confirmada quando a cobertura está completa.": "Partial or failed runs are not counted as zero: no findings are confirmed only with complete coverage.",
  "Resultado parcial: uma etapa de IA foi concluída com cobertura limitada. Os itens exibidos não representam cobertura completa.": "Partial result: an AI stage had limited coverage. Displayed items do not represent complete coverage.",
  "Os resultados serão exibidos aqui conforme as etapas terminarem.": "Results will appear here as stages finish.",
  "Uma etapa teve falha ou cobertura limitada; a ausência de itens não confirma que não existam problemas.": "A stage failed or had limited coverage; no items does not confirm the absence of problems.",
  "Nenhuma recomendação consultiva de desempenho foi identificada nesta execução.": "No consultive performance recommendation was identified in this run.",
  "As verificações determinísticas concluídas não identificaram vulnerabilidades nesta execução.": "Completed deterministic checks found no vulnerabilities in this run.",
  "Não foi possível baixar o relatório PDF.": "Could not download the PDF report.",
  "Não foi possível atualizar o status.": "Could not update the status.",
  "Informe uma observação para registrar a alteração.": "Enter a note to save the change.",
  "Validada automaticamente por análise posterior completa.": "Automatically validated by a later complete analysis.",
  "Recomendação independente, sem ciclo de remediação de vulnerabilidade.": "Independent recommendation without a vulnerability remediation cycle.",
  "A IA está verificando métodos candidatos para vulnerabilidades e melhorias adicionais.": "AI is checking candidate methods for additional vulnerabilities and improvements.",
  "As vulnerabilidades e melhorias das regras já estão disponíveis enquanto a IA avalia os achados.": "Rule findings are available while AI assesses them.",
  "A análise foi interrompida. Consulte os detalhes para ver o motivo.": "Analysis stopped. See details for the reason.",
  "Este sistema ainda não possui análises.": "This system has no analyses yet.",
  "Código Java": "Java code",
  "Execução de comando": "Command execution",
  "Notificações da análise": "Analysis notifications",
  "Não foi possível baixar o relatório.": "Could not download the report.",
  "Não foi possível iniciar a análise": "Could not start analysis",
  "Preparando análise. Solicitando a análise do repositório.": "Preparing analysis. Requesting repository analysis.",
  "Solicitando a análise do repositório…": "Requesting repository analysis…",
  "SHA não informado": "SHA unavailable",
  "Segurança": "Security",
  "segurança": "security",
  "Desempenho": "Performance",
  "desempenho": "performance",
  "Tendência de vulnerabilidades por execução": "Vulnerability trend by run",
  "Concluída": "Completed",
  "padrão": "default",
  "Motivo da avaliação de falso positivo": "Reason for false-positive assessment",
  "Modelo": "Model",
  "Vulnerabilidade": "Vulnerability",
  "A severidade exibida permanece a da regra.": "Displayed severity remains the rule's severity.",
  "Análise concluída.": "Analysis completed.",
  "Análise interrompida.": "Analysis interrupted.",
  "Acompanhando análise de": "Monitoring analysis of",
  "Arquivos Java processados": "Java files processed",
  "Acessando repositório": "Accessing repository",
  "Analisando arquivos Java": "Analyzing Java files",
  "Avaliando vulnerabilidades/melhorias identificadas": "Assessing identified vulnerabilities/improvements",
  "Buscando vulnerabilidades/melhorias adicionais": "Looking for additional vulnerabilities/improvements",
  "Análise concluída": "Analysis completed",
  "Análise interrompida": "Analysis interrupted",
  "Preparando análise": "Preparing analysis",
  "Obtendo os arquivos públicos do repositório com segurança.": "Securely retrieving the repository's public files.",
  "A análise está avançando. Os resultados aparecem nesta página.": "The analysis is progressing. Results will appear on this page.",
  "Não foi possível concluir a solicitação.": "The request could not be completed.",
  "Não foi possível conectar ao sistema. Verifique sua conexão e tente novamente.": "Unable to connect to the system. Check your connection and try again.",
  "Sessão ausente ou expirada": "Session missing or expired",
  "Não foi possível abrir a análise": "The analysis could not be opened",
  "Navegação principal": "Main navigation",
  "Nova análise": "New analysis",
  "Tema da interface": "Interface theme",
  "Tema claro": "Light theme",
  "Tema escuro": "Dark theme",
  "Claro": "Light",
  "Escuro": "Dark",
  "Sair": "Sign out",
  "Carregando sistemas analisados…": "Loading analyzed systems…",
  "Carregando dashboard do sistema…": "Loading system dashboard…",
  "Fundação & Parsers · CP1": "Foundation & Parsers · CP1",
  "Dashboard": "Dashboard",
  "Cadastro realizado com sucesso. Faça login para continuar.": "Registration completed successfully. Sign in to continue.",
  "Sem classificação": "Unclassified",
  "Nenhuma vulnerabilidade ou melhoria foi identificada.": "No vulnerability or improvement was identified.",
  "Verifique os campos informados": "Check the provided fields",
  "Entrada inválida": "Invalid input",
  "Recurso não encontrado": "Resource not found",
};

export function setLocale(locale: string) {
  currentLocale = locale === "en-US" ? "en-US" : "pt-BR";
  if (typeof document !== "undefined") document.documentElement.lang = currentLocale;
  if (currentLocale === "en-US" && typeof document !== "undefined") {
    translatePage();
    if (!observer) {
      observer = new MutationObserver(() => translatePage());
      observer.observe(document.body, { subtree: true, childList: true, characterData: true });
    }
  } else if (observer) {
    observer.disconnect();
    observer = null;
  }
}

export function getLocale(): Locale { return currentLocale; }
export function t(portuguese: string): string {
  return currentLocale === "en-US" ? (messages[portuguese] ?? portuguese) : portuguese;
}

export function translateDynamic(value: string): string {
  return t(value);
}

let observer: MutationObserver | null = null;
const dynamicLabels = ["Maior prioridade", "Linha", "coluna", "Concluída em", "Engine de taint versão", "Modelo", "Página"];

function translatedText(value: string): string {
  const trimmed = value.trim().replace(/\s+/g, " ");
  const exact = messages[trimmed];
  if (exact) return value.replace(/\S[\s\S]*\S|\S/, exact);
  let result = value;
  for (const label of dynamicLabels) {
    const translated = messages[label];
    if (translated) result = result.replace(new RegExp(`\\b${label}(?=[:\\s])`, "g"), translated);
  }
  result = result
    .replace(/\banálise\(s\)/g, "analyses")
    .replace(/\bvulnerabilidade\(s\)\/melhoria\(s\)/g, "vulnerabilities/improvements")
    .replace(/\bvulnerabilidade\(s\)/g, "vulnerabilities")
    .replace(/\barquivo\(s\) com ocorrências/g, "files with occurrences")
    .replace(/\bEm processamento ·/g, "Processing ·")
    .replace(/\bna fila\b/g, "queued")
    .replace(/\bSem prioridade\b/g, "No priority")
    .replace(/\bVoltar para\b/g, "Back to");
  return result;
}

/** Traduz textos e atributos de componentes existentes e inseridos durante as consultas. */
export function translatePage(): void {
  if (currentLocale !== "en-US" || typeof document === "undefined") return;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node: Node | null;
  while ((node = walker.nextNode())) {
    const parent = node.parentElement;
    if (!parent || parent.closest("code, pre, script, style, .java-code")) continue;
    const value = node.nodeValue ?? "";
    const next = translatedText(value);
    if (next !== value) node.nodeValue = next;
  }
  document.querySelectorAll<HTMLElement>("[aria-label], [placeholder], [title]").forEach((element) => {
    for (const attribute of ["aria-label", "placeholder", "title"]) {
      const value = element.getAttribute(attribute);
      if (value) {
        const next = translatedText(value);
        if (next !== value) element.setAttribute(attribute, next);
      }
    }
  });
}
