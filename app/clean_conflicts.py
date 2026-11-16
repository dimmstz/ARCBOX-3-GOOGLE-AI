import os

extensions = ['.kt', '.kts', '.md', '.yaml', '.xml', '.py']
skip_dirs = ['.gradle', 'build', '.git']

for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in skip_dirs]
    for file in files:
        if any(file.endswith(ext) for ext in extensions):
            path = os.path.join(root, file)
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                if '<<<<<<< HEAD' in content:
                    print(f'Cleaning conflicts in {path}')
                    lines = content.splitlines()
                    new_lines = []
                    mode = 'normal'
                    for line in lines:
                        if line.startswith('<<<<<<< HEAD'):
                            mode = 'head'
                            continue
                        elif line.startswith('======='):
                            if mode == 'head':
                                mode = 'remote'
                                continue
                        elif line.startswith('>>>>>>> '):
                            if mode in ['head', 'remote']:
                                mode = 'normal'
                                continue
                        
                        if mode == 'normal':
                            new_lines.append(line)
                        elif mode == 'head':
                            new_lines.append(line)
                        elif mode == 'remote':
                            pass
                    
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write('\n'.join(new_lines) + '\n')
            except Exception as e:
                print(f'Error processing {path}: {e}')
print('Conflict cleanup complete.')
