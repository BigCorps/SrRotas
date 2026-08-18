Sr. Rotas — Fase 1.0.0Plano de arquitetura, trial, TWA/Web, cobrança, antiabuso e lançamentoData da análise: 18/08/2026
Base atual do Sr. Rotas analisada: 0.13.2-beta · versionCode 19
Package Android definitivo: com.srrotas.app
Domínio canônico: https://srrotas.com
Backend/Supabase Sr. Rotas: projeto gheymrttmfdxnjdbgvgl
Referência comparada: repositório + Supabase de produção do MonitorIA (BigCorps/MonitorIA, projeto xwejfayeackbrilipgrj)Este documento define como a 1.0.0 Release Candidate deve ser construída depois da consolidação do Closed Beta 0.13. Ele é um plano de implementação. Nenhuma migration ou alteração de produção foi executada durante esta análise.1. Objetivo da 1.0.0A 1.0.0 deve transformar o Sr. Rotas de um beta funcional em um produto comercial cuja arquitetura tenha três camadas bem separadas:CopiarANDROID KOTLIN
motor técnico e integração com o aparelho
        ↓
WEB / TWA CONFIÁVEL
experiência dinâmica do produto
        ↓
BACKEND / SUPABASE
regra comercial, trial, pagamento, créditos e segurança
