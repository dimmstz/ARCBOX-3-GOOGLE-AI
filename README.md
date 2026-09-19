<p align="center">
  <img src="app/src/main/res/drawable/arcbox_icon.png" width="120" height="120" alt="ArcBox Icon" style="border-radius: 24px;" />
</p>

<h1 align="center">ArcBox File Manager</h1>

<p align="center">
  <strong>Gerenciador de Arquivos Moderno, Seguro e com Atualizações Automáticas via GitHub Releases para Android</strong>
</p>

<p align="center">
  <a href="releases"><img src="https://img.shields.io/badge/Vers%C3%A3o-v1.0.24-blue.svg?style=flat-square&logo=android" alt="Versão v1.0.24"></a>
  <img src="https://img.shields.io/badge/Android-10%20a%2016-green.svg?style=flat-square" alt="Compatibilidade Android">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg?style=flat-square" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Kotlin-100%25-orange.svg?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg?style=flat-square" alt="Licença">
</p>

---

## 🚀 Novidades da Versão v1.0.24

- ☁️ **Atualização e Recarregamento Automático de Arquivos e Pastas no MEGA**:
  - Resolvido o problema de sincronização/cache ao retornar para a pasta do MEGA ou ao voltar do plano de fundo.
  - Implementada invalidação de cache em `onResume()` e atualização em tempo real ao navegar ou puxar para recarregar.
  - Reduzido o TTL de cache in-memory do MEGA de 10 minutos para 15 segundos, garantindo sincronia ágil com a nuvem.
  - Adicionado suporte a estrutura local espelhada como fallback para reconexão rápida.

---

## 📋 Histórico de Versões

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
