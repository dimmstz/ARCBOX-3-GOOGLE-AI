<p align="center">
  <img src="app/src/main/res/drawable/arcbox_icon.png" width="120" height="120" alt="ArcBox Icon" style="border-radius: 24px;" />
</p>

<h1 align="center">ArcBox File Manager</h1>

<p align="center">
  <strong>Gerenciador de Arquivos Moderno, Seguro e com Atualizações Automáticas via GitHub Releases para Android</strong>
</p>

<p align="center">
  <a href="releases"><img src="https://img.shields.io/badge/Vers%C3%A3o-v1.0.15-blue.svg?style=flat-square&logo=android" alt="Versão v1.0.15"></a>
  <img src="https://img.shields.io/badge/Android-10%20a%2016-green.svg?style=flat-square" alt="Compatibilidade Android">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg?style=flat-square" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Kotlin-100%25-orange.svg?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Licen%C3%A7a-MIT-blue.svg?style=flat-square" alt="Licença">
</p>

---

## 🚀 Novidades da Versão v1.0.15

- ⚡ **Carregamento Instantâneo sem Downloads Automáticos**:
  - Removidos completamente todos os downloads automáticos em segundo plano de arquivos de mídia (imagens e fotos) no grid de arquivos da nuvem. Agora, a listagem de diretórios do MEGA é **instantânea e limpa**, sem lag de rede ou consumo indevido de banda para miniaturas.
- ☁️ **Funcionamento Puro em Nuvem (Acesso sob Demanda)**:
  - Arquivos abertos (fotos, vídeos, PDFs, etc.) são mantidos estritamente na memória temporária do sistema (`context.cacheDir`), funcionando de forma idêntica a um navegador web. Nenhum arquivo se acumula permanentemente no armazenamento interno offline do aparelho.
  - O download definitivo de arquivos ocorre apenas quando o usuário clica explicitamente no ícone de "Download" na barra de ações, que salva o arquivo diretamente na pasta pública de *Downloads* do sistema.
- 🧹 **Cálculo e Limpeza Inteligente de Cache**:
  - A tela de Configurações agora calcula em tempo real o espaço exato ocupado pelo cache de miniaturas e arquivos temporários da nuvem.
  - A opção "Limpar" agora esvazia completamente a pasta temporária `cacheDir/cloud_storage` e limpa dados legados, liberando espaço instantaneamente.

---

## 📋 Histórico de Versões

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
