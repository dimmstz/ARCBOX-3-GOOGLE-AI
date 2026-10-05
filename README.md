<p align="center">
  <img src="app/src/main/res/drawable/arcbox_icon.png" width="120" height="120" alt="ArcBox Icon" style="border-radius: 24px;" />
</p>

<h1 align="center">ArcBox File Manager</h1>

<p align="center">
  <strong>Gerenciador de Arquivos Moderno, Seguro e com Atualizações Automáticas via GitHub Releases para Android</strong>
</p>

<p align="center">
  <a href="releases"><img src="https://img.shields.io/badge/Vers%C3%A3o-v1.0.34-blue.svg?style=flat-square&logo=android" alt="Versão v1.0.34"></a>
  <img src="https://img.shields.io/badge/Android-10%20a%2016-green.svg?style=flat-square" alt="Compatibilidade Android">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg?style=flat-square" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Kotlin-100%25-orange.svg?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg?style=flat-square" alt="Licença">
</p>

---

## 🚀 Novidades da Versão v1.0.34

- ☁️ **Exibição Completa de Todas as Pastas do MEGA**:
  - **Varredura Completa da Árvore de Diretórios**: Algoritmo aprimorado para decodificar, mapear e listar todas as pastas e subpastas no MEGA, incluindo pastas raiz, compartilhamentos e nós órfãos.
  - **Decriptografia Multichave Aprimorada**: Suporte a chaves de múltiplos usuários em pastas compartilhadas e resolução de nomes hierárquicos sem perdas.
- ⏳ **Círculo de Carregamento Centralizado com Espera de Conexão**:
  - **Feedback Visual Claro**: Indicador de progresso circular (`CircularProgressIndicator`) no meio da tela acompanhado de mensagens informativas enquanto o aplicativo aguarda a conexão e sincroniza as pastas do MEGA e das demais nuvens.
  - **Transição Fluida**: Navegação instantânea e sincronização em tempo real ao alternar entre unidades de armazenamento e navegar por subpastas.

---

## 📋 Histórico de Versões

### v1.0.33
- ☁️ **Auditoria Completa e Correção na Listagem de Nuvens**:
  - **Exibição Garantida de Arquivos e Pastas**: Resolução definitiva da visualização de arquivos e subpastas em todas as contas conectadas (MEGA, Google Drive, OneDrive, Dropbox, MediaFire e WebDAV).
  - **Mesclagem Inteligente de Arquivos Remotos e Locais**: O repositório agora combina arquivos remotos recebidos via API com pastas e arquivos criados localmente pelo usuário.
  - **Preservação Integral de Diretórios do Usuário**: Removida qualquer exclusão indevida de pastas locais, mantendo seguras todas as estruturas de diretórios criadas.
- 🔑 **Conexão com Senha Salva / Autofill do Android**:
  - Captura automática de credenciais do gerenciador de senhas do Google no WebView e suporte a sessões web em todos os provedores.

### v1.0.32
- ☁️ **Remoção Completa de Mocks e Sincronização Pura no MEGA**:
  - Eliminação total de arquivos e pastas simulados (`ArcBox_MEGA_Note.txt`, `Documentos`, `Downloads`, `Imagens` e notas do MediaFire).
  - O aplicativo lista e exibe exclusivamente os nós e arquivos reais mantidos na sua conta remota do MEGA via `MegaApiClient`.
  - Limpeza automática de quaisquer resquícios de arquivos mock legados armazenados no cache da aplicação.

### v1.0.31
- 🎴 **Detecção Multinível e Conexão de Cartão SD e Drives OTG**:
  - Integração nativa com a API `android.os.storage.StorageManager.storageVolumes` (API 24+) para identificação imediata de cartões de memória externos e pendrives USB em todas as versões do Android.
  - Medição de espaço e leitura garantida via `getExternalFilesDirs` no caminho do aplicativo no cartão SD.
- 📊 **Cálculo Resiliente e Visualização Completa de Espaço de Armazenamento**:
  - Correção da exibição no menu lateral (Drawer): os valores de gigabytes e porcentagem (*"X GB de Y GB usados (Z%)"*) são calculados dinamicamente.
- 🔄 **Sincronização no Ciclo de Vida (`onResume`)**:
  - Reconhecimento e atualização instantânea de unidades de armazenamento ao retornar de configurações ou alternar aplicativos.

### v1.0.30
- 🎵 **Interface Fixa e Acessível no Reprodutor de Música**:
  - Removida a transição automática para tela cheia / modo escuro imersivo durante a reprodução de áudio. Controles de reprodução permanecem visíveis e acessíveis.
- 🔔 **Widget de Controle de Mídia nas Notificações do Android**:
  - Adicionado player interativo na barra de notificações com botões de ação: Reproduzir/Pausar, Próxima Faixa, Faixa Anterior e Fechar.
- 🔄 **Correção Definitiva da Verificação de Atualizações via GitHub**:
  - Repositório com visibilidade pública, download direto por CDN sem necessidade de token para usuários finais e tratamento adequado de status na interface.

### v1.0.29
- 🎴 **Detecção Resiliente e Conexão de Cartão SD Externo (Android 11 a 15)**:
  - Eliminação da trava preliminar de leitura restritiva na inicialização de volumes externos, garantindo que o Cartão SD seja prontamente listado e reconhecido pelo aplicativo.
  - Exibição de orientações contextuais claras em caso de necessidade de concessão da permissão especial "Acesso a todos os arquivos" (`MANAGE_EXTERNAL_STORAGE`).
- ⚡ **Estabilidade na Análise de Espaço e Prevenção de Bloqueios**:
  - Implementação de tempo limite seguro de 8 segundos no escaneamento detalhado de armazenamento, evitando que o painel fique em carregamento contínuo em mídias lentas ou protegidas.
  - Finalização garantida do estado de progresso com blocos de tratamento de exceção resilientes no ViewModel.
- 🛠️ **Estabilização da Compilação e Suporte KSP**:
  - Ajuste nas configurações do Gradle e alinhamento do Kotlin 2.1.0 com KSP, solucionando falhas no processamento de símbolos e build do Android 15 (Target SDK 35).

---

### v1.0.28
- ☁️ **Card "Adicionar Nuvem" Contínuo e Inteligente no Menu Lateral**:
  - O card de adicionar nuvem permanece visível enquanto houver provedores disponíveis para conexão (MEGA, Google Drive, OneDrive, Dropbox, MediaFire, WebDAV).
  - Listagem dinâmica no subtítulo dos serviços restantes para conexão rápida.
  - Ocultação automática quando todos os serviços estiverem vinculados e reaparecimento instantâneo caso qualquer nuvem seja desconectada.
- 🛡️ **Compatibilidade de Tema e Estabilidade de Inicialização**:
  - Tema base atualizado para `Theme.AppCompat.DayNight.NoActionBar`, eliminando crashes de inicialização com `FragmentActivity` e biometria.
  - Renderização nativa da tela de bloqueio e tratamento de fallback seguro sem travar a interface.
- ⚡ **Otimização do Processamento de Build**:
  - Limpeza de dependências desnecessárias do processador de anotações KSP, acelerando e estabilizando a compilação do projeto.

---

### v1.0.27
- 🔄 **Correção e Estabilização das Atualizações In-App via GitHub**:
  - Ajuste na codificação e autenticação do token para busca e download direto de APKs em repositórios privados.
  - Correção no redirecionamento do download de releases protegidas para garantir instalação sem erros.
  - Sincronização e validação resiliente de credenciais no fluxo de verificação de atualizações.

---

## 📋 Histórico de Versões

### v1.0.26
- 🔑 **Integração com Senhas Salvas do Google / Android**:
  - Suporte a preenchimento automático pelo Gerenciador de Senhas do Google / Android no modal de conexão de nuvens (MediaFire, MEGA, Google Drive, Microsoft OneDrive, Dropbox e WebDAV).
  - Autenticação web integrada com detecção de credenciais para login rápido em 1 toque.
- 🧹 **Interface Limpa e Direta de Armazenamento em Nuvem**:
  - Remoção de banners redundantes e do seletor SAF antigo da tela principal de nuvens.
  - Acesso direto aos cartões de cada provedor com layout intuitivo e padronizado.

---

## 📋 Histórico de Versões

### v1.0.25
- ☁️ **Botão "Adicionar Nuvem" Visível no Menu Lateral Desde o Primeiro Acesso**:
  - Exibição de card interativo com botão de conexão em destaque logo abaixo da seção Nuvem & Armazenamento no primeiro acesso ao app, facilitando vincular MEGA, Google Drive, OneDrive ou WebDAV rapidamente.
- 🔄 **Atualização do Token de Releases e Tratamento Elegante de Erros**:
  - Novo token de autenticação oficial integrado para verificação de atualizações.
  - Migração e substituição automática do token nos dispositivos que continham o token antigo salvo em cache.
  - Sanitização de mensagens de erro: removidos jargões e referências internas ao GitHub em caso de falha, apresentando comunicados amigáveis e transparentes ao usuário final.

### v1.0.24
- ☁️ **Atualização e Recarregamento Automático de Arquivos e Pastas no MEGA**:
  - Resolvido o problema de sincronização/cache ao retornar para a pasta do MEGA ou ao voltar do plano de fundo.
  - Implementada invalidação de cache em `onResume()` e atualização em tempo real ao navegar ou puxar para recarregar.
  - Reduzido o TTL de cache in-memory do MEGA de 10 minutos para 15 segundos, garantindo sincronia ágil com a nuvem.
  - Adicionado suporte a estrutura local espelhada como fallback para reconexão rápida.

### v1.0.23
- 🛠️ **Correção da Validação do Schema do Codemagic (`codemagic.yaml`)**:
  - Ajustada a estrutura de publicação no pipeline do Codemagic CI/CD, eliminando chaves incompatíveis e garantindo conformidade estrita com o schema oficial.
  - Implementada etapa de publicação segura via script para GitHub Releases.
  - Suporte completo a build de CI, Release (APK + AAB) e Nightly QA.

---

## 📋 Histórico de Versões

### v1.0.22
- 📱 **Aprimoramento Visual e Redimensionamento do Splash Screen (160dp)**:
  - Splash Screen configurado especificamente para **160dp** (`splash_icon_large`), garantindo abertura limpa, impactante e sem interferência da camada do launcher.
  - Ícone principal adaptativo fixado em **80dp**, centralizado e sem distorções nem recortes da arte original.
- ⚙️ **Configuração Completa de CI/CD para Codemagic (`codemagic.yaml`)**:
  - Implementação de pipelines automatizados de build para Android.

---

## 📋 Histórico de Versões

### v1.0.21
- 🎬 **Prevenção de Arquivos Parciais e Reprodução Segura no MEGA**:
  - Implementado sistema atômico de download com arquivos temporários `.part`, garantindo que vídeos e imagens do **MEGA** sejam executados pelo player e decodificados apenas após 100% do download e descriptografia concluídos.
  - Adicionada tela com barra de progresso em tempo real e opção de cancelamento para mídias em nuvem no visualizador.
  - Otimização do semáforo de miniaturas com limites inteligentes de tamanho e debounce contra travamentos em rolagem rápida.
- 📂 **Correção e Conexão Rápida para MediaFire**:
  - Resolvido problema de sobrescrita e erro de leitura no cache local do MediaFire.
  - Habilitada a **Conexão Rápida (Android SAF)** para o MediaFire em gerenciador de contas e menu lateral.
  - Suporte completo a abertura de arquivos remotos via aplicativos externos e visualizador de documentos.

### v1.0.20
- ☁️ **Vinculação de Nuvens em 1 Toque (Android SAF Nativo)**:
  - Integração facilitada com **Google Drive**, **OneDrive** e **Dropbox** usando o Storage Access Framework nativo do sistema Android.
  - Conexão e sincronização direta sem exigir criação manual de API keys ou credenciais de desenvolvedor.
  - Navegação fluida e transparente pelas pastas `/cloud/drive`, `/cloud/onedrive` e `/cloud/dropbox` com ponte automática para os diretórios SAF registrados.
- 🛠️ **Padronização e Correção dos Provedores em Nuvem**:
  - Correção na resolução de caminhos e normalização de subpastas (`cleanSub`) no **OneDrive**, **Dropbox** e **WebDAV**.
  - Cache dinâmico inteligente com invalidação adequada durante navegação profunda.
- 🌐 **Aprimoramentos no WebDAV Personalizado**:
  - Implementação completa do protocolo com suporte a requisições `PROPFIND` com profundidade 1 e tratamento de respostas HTTP `207 Multi-Status`.
  - Suporte a credenciais HTTP Basic, streaming de download e download direto.

### v1.0.19
- 🛠️ **Correção Crítica no Instalador de Atualizações Automáticas (Erro de Downgrade Bloqueado)**:
  - Corrigido um problema onde o ArcBox baixava erroneamente uma build temporária de debug do GitHub Action (`app-debug.apk`) em vez da release de produção oficial (`Arcbox-v1.X.X-release.apk`).
  - O uso do APK de debug resultava em um código de versão desatualizado, ativando indevidamente a trava de segurança "Downgrades são impedidos por segurança" (Ex: `A versão baixada (16) é inferior à versão instalada (18)`).
  - O gerenciador de atualizações OTA do ArcBox foi reprogramado para ignorar os arquivos de debug e exigir, com prioridade máxima, as builds nominais de lançamento.

### v1.0.18
- ⚡ **Super Otimização de Velocidade e Download Concorrente**: Dispatcher (OkHttp) ampliado para 128 requisições simultâneas (32 por host), pool de sockets aprimorado, e buffers refinados (64 KB). Semáforo de processamento ampliado (Semaphore=6).

### v1.0.17
- ⚡ **Pré-Carregamento de Miniaturas e Debounce (150ms)**: Introduzido o pré-carregamento assíncrono condicionado por tamanho de mídias (imagens < 12MB, vídeos < 8MB) para visualização rápida.

### v1.0.16
- ⌨️ **Otimização de Foco e Transições do Teclado (IME Insets)**: Correção de timeouts de animação do Android FrameTracker (`IME_INSETS_HIDE_ANIMATION`) adicionando ações "Done" em todos os formulários e caixas de diálogo.

### v1.0.15
- ⚡ **Carregamento Instantâneo sem Downloads Automáticos**: Removidos completamente os downloads automáticos em segundo plano de imagens e fotos para miniaturas de mídias em nuvem.
- ☁️ **Funcionamento Puro em Nuvem**: Arquivos visualizados sob demanda salvos em memória temporária (`cacheDir/cloud_storage`), sem acumular memória permanentemente.
- 🧹 **Limpeza Inteligente de Cache**: Tela de Configurações calcula o espaço de cache e limpa diretórios temporários instantaneamente.

### v1.0.14
- ⚡ **Navegação Instantânea de Pastas na Nuvem (Cache de 10 min)**: Expansão do TTL de cache das listagens de diretórios de todos os provedores em nuvem de 1 para 10 minutos.
- 🖼️ **Otimização de Resolução e Tamanho de Miniaturas**: Ajuste do limite de download automático para 5MB e prevenção de erros de I/O em segundo plano no Coil.

### v1.0.13
- 🚀 **Abertura Ultrarrápida de Vídeos e Imagens no MEGA**: Removido download de vídeos em segundo plano para miniaturas e incluído semáforo de concorrência controlada para fotos.
- ⚡ **Otimização de Buffers I/O de Leitura/Decriptação**: Buffers expandidos de 64KB para 256KB para reduzir chamadas de sistema I/O.

### v1.0.12
- ⚡ **Abertura e Streaming Instantâneo no MEGA Cloud**: Validação e decodificação otimizada de nós e mídias.
- ☁️ **Exibição Dinâmica de Discos em Nuvem**: Nuvens aparecem somente quando conectadas e ativas.

### v1.0.11
- ☁️ **Exibição Dinâmica de Armazenamento em Nuvem**: Discos de nuvem aparecem apenas quando conectados.
- ➕ **Atalho "+ Adicionar Nuvem"**: Acesso rápido em toda a interface para vincular novas contas.

### v1.0.10
- ☁️ **Estabilidade no Provedor de Nuvem**: Correção na revalidação de sessões de nuvem e fallbacks de cache offline.
- 🐛 **Resolução de Erros de Transição**: Ajustes refinados no gerenciador de estados das animações.

### v1.0.9
- 🎨 **Splash Screen com Fundo Branco**: O fundo de abertura atrás do ícone da aplicação foi ajustado para branco puro (`@android:color/white`) em todas as versões do Android.
- ☁️ **Correção Crítica no Download do MEGA Cloud**: Streaming e decodificação contínua em tempo real com integridade de dados garantida.

### v1.0.7
- 🎛️ **Botão Liga/Desliga para Transições**: Interruptor moderno (*Switch*) no cabeçalho do cartão "Transição entre Pastas" nas Configurações.
- ⏱️ **Tempo de Transição Calibrado**: Duração aumentada (340ms a 400ms) com curvas *Emphasized Decelerate* e *FastOutSlowInEasing*.
- 🚀 **Rolagem Rápida a 120Hz**: Algoritmo com fading suave e congelamento temporário de thumbnails durante fling rápido.

---

## 🚀 Sobre o ArcBox

O **ArcBox** é um gerenciador de arquivos nativo para Android desenvolvido com foco em desempenho extremo, fluidez a 120Hz, privacidade, design Material 3 e independência da Google Play Store através de um sistema nativo de auto-atualização conectado diretamente às **GitHub Releases**.

---

## ✨ Principais Funcionalidades

- 📁 **Navegação Ultrarrápida de Arquivos**: Gerenciamento de arquivos e pastas internos, cartão SD, OTG e partições de sistema com Root com rolagem ultra suave (zero recomposição) e transições contextuais aceleradas por hardware.
- ⚡ **Auto-Update via GitHub Releases**:
  - Consulta automática e silenciosa da versão mais recente via API oficial do GitHub.
  - Download em background com barra de progresso e cancelamento.
  - Verificação de integridade via **SHA-256** e checagem de assinatura de pacote (`PackageManager`).
  - Suporte a atualizações opcionais e **atualizações obrigatórias** (`[MANDATORY]`).
  - Alternadores nas Configurações: *Atualizações Automáticas*, *Download Apenas no Wi-Fi* e *Servidor Customizado*.
- 🔒 **Cofre Seguro com Biometria**: Proteja arquivos sensíveis com criptografia e autenticação biométrica (Impressão digital / Face).
- ☁️ **Integração com Nuvens & Armazenamento Remoto**:
  - Google Drive, MEGA, Dropbox, OneDrive, MediaFire e WebDAV (Nextcloud / OwnCloud).
- 📦 **Compactador e Descompactador**: Suporte nativo a arquivos `.zip`, `.tar`, `.gz` com pré-visualização de conteúdo.
- 🎨 **Personalização Material Design 3**:
  - Tema Claro, Escuro e Dinâmico (Monet).
  - Cores de destaque customizáveis (Violeta, Esmeralda, Oceano, Rubi, Âmbar).
  - Transições fluidas e dinâmicas de pastas com curvas M3 e profundidade de navegação (Slide, Fade, Zoom, Efeito Pilha).
  - Barra de rolagem rápida suave com fading automático.
- 🗑️ **Lixeira Inteligente**: Recuperação rápida de arquivos excluídos com limpeza automática configurável.
- 📊 **Dashboard de Armazenamento**: Gráficos visuais de ocupação por tipo de mídia (Vídeos, Imagens, Áudios, Documentos, APKs).

---

## 🛠️ Tecnologias e Arquitetura

- **Linguagem**: Kotlin 100%
- **Interface**: Jetpack Compose com Material Design 3 (M3)
- **Arquitetura**: MVVM (Model-View-ViewModel) com Coroutines e `StateFlow`
- **Persistência**: Room Database & Android SharedPreferences
- **Rede & Atualizações**: HttpsURLConnection com TLS Seguro & API GitHub v3
- **Compatibilidade**: Android 10 (API 24+) até Android 16 (API 36)

---

## 📥 Como Baixar e Instalar

1. Baixe o APK oficial mais recente na aba de [**Releases**](releases).
2. Abra o arquivo `.apk` no seu dispositivo Android.
3. Se solicitado, autorize a permissão de *"Instalar apps desconhecidos"* para o seu navegador ou gerenciador.
4. Conclua a instalação. O ArcBox continuará se atualizando automaticamente a partir desta versão!

---

## ⚙️ Como Publicar uma Nova Versão (Guia do Desenvolvedor)

### 1. Atualize a versão no `build.gradle.kts`:
```kotlin
defaultConfig {
    versionCode = 2
    versionName = "1.1.0"
}
```

### 2. Gere o APK assinado ou de release:
```bash
gradle assembleRelease
```
O APK será gerado em: `app/build/outputs/apk/release/app-release.apk` (ou `assembleDebug`).

### 3. Crie a Release no GitHub:
1. No seu repositório GitHub, acesse a aba **Releases** e clique em **"Draft a new release"** (ou **"Create a new release"**).
2. Escolha uma tag no formato `v1.0.0` (ou `v1.1.0`).
3. Dê um título para a release (ex: `ArcBox v1.0.0 - Lançamento Oficial`).
4. Escreva as novidades no Changelog. *(Para atualização obrigatória, inclua `[MANDATORY]` no texto)*.
5. Anexe o arquivo `.apk`.
6. Clique em **"Publish release"**.

O ArcBox em todos os dispositivos instalados detectará a nova versão automaticamente!

---

## 🔒 Segurança e Privacidade

- O ArcBox **não coleta telemetria pessoal**.
- Toda a comunicação de atualizações é feita diretamente entre o dispositivo do usuário e os servidores HTTPS oficiais do GitHub (`api.github.com` e `objects.githubusercontent.com`).
- A integridade do instalador é checada antes de abrir o instalador de pacotes do sistema.

---

## 📄 Licença

Distribuído sob a licença MIT.
