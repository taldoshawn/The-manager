# Segurança

## Modelo de privilégios

- Normal é o modo padrão.
- Root, Shizuku e ADB exigem ativação explícita do usuário.
- Trocar o modo da interface não concede permissão; o backend correspondente precisa estar conectado.
- A chave privada do ADB é gerada no Android Keystore, não é exportável e não é enviada pela rede.
- O app não inclui analytics, anúncios ou trackers.

## Operações destrutivas

A interface exige confirmação e o backend recusa exclusão ou movimentação das raízes `/`, `/system`, `/system_ext`, `/vendor`, `/product`, `/data`, `/storage`, `/sdcard`, `/storage/emulated` e `/storage/emulated/0` como um todo.

Caminhos enviados a Root/ADB são normalizados e escapados. Nomes novos não podem conter `/`, NUL, `.` ou `..`. Arquivos são salvos em um temporário no mesmo diretório e substituídos por rename somente após flush/fsync.

## Relatar vulnerabilidade

Não abra uma issue pública contendo exploit funcional, credenciais ou dados pessoais. Use o canal privado de security advisories do GitHub deste repositório.
