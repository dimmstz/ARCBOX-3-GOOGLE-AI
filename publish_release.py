import urllib.request, json, os

token = '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'
repo = 'dimmstz/ARCBOX-3-GOOGLE-AI'
tag = 'v1.0.21'
name = 'Arcbox File Manager v1.0.21'
body = """## 🚀 Novidades da Versão v1.0.21

- 🎬 **Prevenção de Arquivos Parciais e Reprodução Segura no MEGA**:
  - Implementado sistema atômico de download com arquivos temporários `.part`, garantindo que vídeos e imagens do **MEGA** sejam executados pelo player e decodificados apenas após 100% do download e descriptografia concluídos.
  - Adicionada tela com barra de progresso em tempo real e opção de cancelamento para mídias em nuvem no visualizador.
  - Otimização do semáforo de miniaturas com limites inteligentes de tamanho e debounce contra travamentos em rolagem rápida.
- 📂 **Correção e Conexão Rápida para MediaFire**:
  - Resolvido problema de sobrescrita e erro de leitura no cache local do MediaFire.
  - Habilitada a **Conexão Rápida (Android SAF)** para o MediaFire em gerenciador de contas e menu lateral.
  - Suporte completo a abertura de arquivos remotos via aplicativos externos e visualizador de documentos.
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
