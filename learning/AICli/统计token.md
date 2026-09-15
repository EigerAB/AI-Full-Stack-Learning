
![[token-usage.png]]

devin

```bash
npx @token-stats/cli@latest monthly `
  --client devin-cli `
  --since 2026-08-01 `
  --until 2026-08-31 `
  --light
```

如果报错，则可能是devin未配置环境变量or存储地址非默认


cc、codex
```bash
npx ccusage@latest monthly
```