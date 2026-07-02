# GitHub Upload Guide

This guide covers the two GitHub pages for this project:

1. Personal profile page: `yaokai123/yaokai123`
2. Project repository page: the repository that contains this codebase

## 1. Publish the personal profile page

GitHub profile README only works in a repository named exactly the same as the account:

```text
yaokai123/yaokai123
```

Use this file as the root README of that repository:

```text
docs/github-profile/README.md
```

After committing it, refresh:

```text
https://github.com/yaokai123
```

## 2. Publish this project repository

Recommended repository name:

```text
multimodal-mindcare-agent
```

Recommended remote:

```text
https://github.com/yaokai123/multimodal-mindcare-agent.git
```

The project homepage is the root README:

```text
README.md
```

## 3. Do not upload local secrets or large files

Before pushing, confirm these files are ignored:

```text
.env
data/
target/
.m2/
*.gguf
*.gguf.zip
scripts/start-voice-dev.ps1
```

These files contain local runtime data, credentials, or large model weights and should not be uploaded to GitHub.

## 4. Suggested first commit

```powershell
git init
git add README.md pom.xml Dockerfile docker-compose.yml .gitignore .env.example src docs scripts models
git status
git commit -m "Initial publish of multimodal mindcare agent"
git branch -M main
git remote add origin https://github.com/yaokai123/multimodal-mindcare-agent.git
git push -u origin main
```

If the repository already exists, skip `git init` and `git remote add origin`, then commit and push normally.

## 5. Recommended GitHub repository settings

- Add topics: `ai-agent`, `rag`, `spring-boot`, `ollama`, `multimodal-ai`, `voice-ai`, `mental-health-tech`
- Add an About description:

```text
Multimodal campus mental-health AI support system with RAG, risk workflow, voice architecture, and admin case management.
```

- Add a license before making the repository public.
