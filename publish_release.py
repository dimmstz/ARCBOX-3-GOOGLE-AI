import urllib.request, json, os

token = os.environ.get('GITHUB_TOKEN', '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')
repo = 'dimmstz/ARCBOX-3-GOOGLE-AI'
tag = 'v1.0.30'
name = 'Arcbox File Manager v1.0.30'
body = """## 🚀 Novidades da Versão v1.0.30

- 🎵 **Interface Fixa e Acessível no Reprodutor de Música**:
  - Removida a transição automática para tela cheia / modo escuro imersivo durante a reprodução de áudio. Todos os controles (Play, Pause, Avançar, Voltar, Loop, Barra de Progresso, Tempo e Botão Fechar) permanecem visíveis e acessíveis sem ocultação repentina.
- 🔔 **Widget de Controle de Mídia nas Notificações do Android**:
  - Adicionado player interativo na barra de notificações com botões de ação: Reproduzir/Pausar, Próxima Faixa, Faixa Anterior / Retrocesso e Fechar.
  - Suporte completo tanto para músicas quanto para vídeos em segundo plano, com estilo de mídia nativo do Android e integração fluida.
- 🔄 **Correção Definitiva da Verificação de Atualizações via GitHub**:
  - Resolução da falha "Nenhuma nova versão encontrada no momento" ao verificar atualizações: o repositório oficial de releases agora possui visibilidade pública, permitindo que todas as versões instaladas (incluindo v1.0.26) localizem e baixem as novas versões instantaneamente.
  - Implementado sistema de fallback multi-token e download direto via link CDN público (`browser_download_url`), garantindo que downloads de atualizações nunca falhem por expiração de credencial.
  - Ajuste na interface do painel de atualizações: mensagens de status quando o app já está atualizado agora exibem confirmação com ícone de sucesso em vez de indicar falha indevida.
"""

url = f'https://api.github.com/repos/{repo}/releases'
payload = json.dumps({
    'tag_name': tag,
    'name': name,
    'body': body,
    'draft': False,
    'prerelease': False
}).encode('utf-8')

req = urllib.request.Request(url, data=payload, headers={
    'Authorization': f'Bearer {token}',
    'Accept': 'application/vnd.github+json',
    'Content-Type': 'application/json',
    'User-Agent': 'Python-Script'
})

upload_url = None
try:
    with urllib.request.urlopen(req) as resp:
        res_data = json.loads(resp.read().decode('utf-8'))
        upload_url = res_data['upload_url'].split('{')[0]
        release_id = res_data['id']
        print(f'Release created successfully! ID: {release_id}')
except urllib.error.HTTPError as e:
    err_body = e.read().decode('utf-8')
    print(f'HTTP Error {e.code}: {err_body}')
    if e.code == 422:
        get_req = urllib.request.Request(f'https://api.github.com/repos/{repo}/releases/tags/{tag}', headers={
            'Authorization': f'Bearer {token}',
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'Python-Script'
        })
        try:
            with urllib.request.urlopen(get_req) as gresp:
                res_data = json.loads(gresp.read().decode('utf-8'))
                upload_url = res_data['upload_url'].split('{')[0]
                release_id = res_data['id']
                print(f'Existing release found! Upload URL: {upload_url}')
        except Exception as ge:
            print('Failed to fetch existing release:', ge)

def upload_asset(upload_url, file_path, asset_name):
    if not os.path.exists(file_path):
        print(f'File not found: {file_path}')
        return
    file_size = os.path.getsize(file_path)
    print(f'Uploading {file_path} as {asset_name} ({file_size} bytes)...')
    upload_asset_url = f'{upload_url}?name={asset_name}'
    with open(file_path, 'rb') as f:
        file_data = f.read()
    up_req = urllib.request.Request(upload_asset_url, data=file_data, headers={
        'Authorization': f'Bearer {token}',
        'Accept': 'application/vnd.github+json',
        'Content-Type': 'application/vnd.android.package-archive',
        'User-Agent': 'Python-Script'
    })
    try:
        with urllib.request.urlopen(up_req) as up_resp:
            up_res = json.loads(up_resp.read().decode('utf-8'))
            print(f'Asset {asset_name} uploaded successfully! Download URL: {up_res.get("browser_download_url")}')
    except urllib.error.HTTPError as ue:
        print(f'Asset {asset_name} upload error {ue.code}: {ue.read().decode("utf-8")}')

if upload_url:
    release_apk = 'app/build/outputs/apk/release/app-release.apk'
    debug_apk = 'app/build/outputs/apk/debug/app-debug.apk'

    if os.path.exists(release_apk):
        upload_asset(upload_url, release_apk, f'Arcbox-{tag}-release.apk')
        upload_asset(upload_url, release_apk, 'app-release.apk')
    else:
        print(f'Release APK not found at {release_apk}')

    if os.path.exists(debug_apk):
        upload_asset(upload_url, debug_apk, 'app-debug.apk')
    else:
        print(f'Debug APK not found at {debug_apk}')
else:
    print('Failed to resolve release upload URL.')
