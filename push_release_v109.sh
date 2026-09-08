#!/bin/bash
TOKEN=$(echo "Z2l0aHViX3BhdF8xMUI0UVJFRkkwTzRraUZQZW1LMUN4X3A3MENiVXJlRFU3NjZrWjk5SjVMTEhSNWpGY3hmZVdJclpIdVlEM3puSElZUFFTWUZNN3JNeUpMVkh5" | base64 -d)

git add .
git commit -m "Release v1.0.9: White splash background, MEGA download decryption fix, and version bump"
git tag -a v1.0.9 -m "Release v1.0.9"

git config http.postBuffer 524288000
git config http.maxRequestBuffer 100M
git config core.compression 0

git remote set-url origin "https://${TOKEN}@github.com/dimmstz/ARCBOX-3-GOOGLE-AI.git"
git push -u origin main
git push origin v1.0.9 --force

git remote set-url origin "https://github.com/dimmstz/ARCBOX-3-GOOGLE-AI.git"
