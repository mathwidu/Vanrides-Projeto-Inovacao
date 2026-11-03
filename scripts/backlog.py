#!/usr/bin/env python3
"""Gera a projeção legível do backlog. Não acessa nem altera o GitHub."""
import argparse
import collections
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = "https://github.com/mathwidu/Vanrides-Projeto-Inovacao"


def load():
    data = json.loads((ROOT / "docs/backlog/tarefas.json").read_text(encoding="utf-8"))
    progress = json.loads((ROOT / "docs/backlog/acompanhamento.json").read_text(encoding="utf-8"))
    specs = json.loads((ROOT / "docs/backlog/criterios.json").read_text(encoding="utf-8"))
    tasks = {t[0]: t for t in data["tasks"]}
    status = {t["taskId"]: t for t in progress["tasks"]}
    criteria = {t["taskId"]: t for t in specs["tasks"]}
    assert len(tasks) == len(data["tasks"]), "IDs duplicados nas tarefas"
    assert len(status) == len(progress["tasks"]), "IDs duplicados no acompanhamento"
    assert len(criteria) == len(specs["tasks"]), "IDs duplicados nos critérios"
    assert len(tasks) == len(status) == len(criteria)
    assert set(tasks) == set(status) == set(criteria)
    assert all(len(t["acceptanceCriteria"]) >= 1 for t in criteria.values())
    numbers = [t[7] for t in tasks.values() if isinstance(t[7], int)]
    assert len(numbers) == len(set(numbers)), "Uma issue não pode representar duas tarefas"
    return data, tasks, status, criteria


def issue_body(task, item, spec):
    def bullets(values, check=False):
        return "\n".join(("- [ ] " if check else "- ") + x for x in values)
    paths = list(dict.fromkeys(item.get("sourcePaths", []) + item.get("documentationPaths", [])))[:4]
    links = []
    for path in paths:
        if (ROOT / path).exists():
            links.append(f"[{path}]({REPO}/blob/main/{path})")
    return (
        f"<!-- vanrides-task:{task[0]} -->\n"
        f"**{task[0]} · {task[2]} · Onda {task[1]} · {item['component']}**\n\n"
        f"## O que resolve\n\n{spec['problem']}\n\n"
        f"**Resultado esperado:** {spec['outcome']}\n\n"
        f"## Situação de partida\n\n"
        f"- Implementação: **{item['implementationStatus']}**. {item.get('implementationNote', '')}\n"
        f"- Validação: **{item['validationStatus']}**. {item.get('validationNote', '')}\n\n"
        "Esse registro vem dos ensaios anteriores. Não é aceite automático da tarefa nem do piloto.\n\n"
        f"## Escopo\n\n{bullets(spec['inScope'])}\n\n"
        f"Fora desta tarefa:\n\n{bullets(spec['outOfScope'])}\n\n"
        f"## Critérios de conclusão\n\n{bullets(spec['acceptanceCriteria'], True)}\n\n"
        "Marcar cada critério somente após revisão da prova correspondente. Registrar PR/commit, comando ou cenário, resultado e limite do teste.\n\n"
        f"## Como testar\n\n{bullets(spec['verification'])}\n\n"
        f"## Dependências e decisões\n\n{bullets(spec['prerequisites'])}\n\n"
        f"{spec.get('decisionPending') or 'Nenhuma decisão adicional identificada para este objetivo.'}\n\n"
        f"## Próxima ação\n\n{item['nextAction']}\n\n"
        f"## Referências\n\n[Especificação]({REPO}/blob/main/docs/08-especificacoes-mvp.md#{task[0].lower()}) · "
        f"[Como trabalhar no backlog]({REPO}/blob/main/docs/07-como-ler-o-backlog.md)\n\n"
        + (bullets(links) + "\n\n" if links else "")
        + f"Estimativa: **{task[5]}**. Responsável registrado: **{task[6] or 'a combinar com o grupo'}**. "
        "Nenhuma atribuição GitHub é feita a partir do nome na planilha.\n"
    )


def render():
    data, tasks, status, specs = load()
    counts = collections.Counter(i["implementationStatus"] for i in status.values())
    out = ["# Backlog do MVP", "", "O backlog descreve trabalho; implementação e validação são informações separadas.",
           "As issues são o espaço de discussão e revisão. A planilha mantém o formato do professor.", "",
           "## Por onde começar", "",
           "1. Leia a funcionalidade e seus critérios na issue. Confira se pertence ao objetivo ativo do programa.",
           "2. Combine com o grupo quem implementa e quem revisa; registre os arquivos envolvidos.",
           "3. Reproduza a situação atual e registre a lacuna antes de alterar código.",
           "4. Abra um PR pequeno com resultado, teste e limitações. A CI não substitui o aceite específico.", "",
           "| Frente | Próximo resultado necessário | Dependência |", "|---|---|---|",
           "| Cadastro e presença | Repetir as conversas no canal real | Linha dedicada e participantes autorizados |",
           "| Aproximação | Concluir regras, testes e calibração do aviso | Posição recente, ordem de pontos e parâmetros revisados |",
           "| Encerramento | Implementar a regra de fim e confirmar a parada da coleta | Critérios de chegada e recuperação |",
           "| Android e iPhone | Percurso completo, rede, bateria e destinatário correto | Funções integradas; testes físicos iPhone pausados |",
           "| Contribuição do grupo | Reproduzir uma frente e enviar o primeiro PR | Guia de desenvolvimento e revisão de um colega |", "",
           "## Como interpretar a situação", "",
           "| Implementação | Quantidade | O que falta para encerrar |", "|---|---:|---|",
           f"| Implementado | {counts['Implementado']} | Conferir escopo e provas específicas; não significa aceite real |",
           f"| Parcial | {counts['Parcial']} | Completar a lacuna descrita e testar |",
           f"| Planejado | {counts['Planejado']} | Definir execução, implementar e validar |",
           f"| Proposta de escopo | {counts['Proposta de escopo']} | Decidir com o grupo antes de implementar |", "",
           "Essas contagens não medem percentual concluído. Teste automatizado, laboratório, aparelho parado e percurso são provas diferentes.",
           "Uma tarefa só termina quando seus critérios são atendidos, o PR foi revisado e a prova registra a versão avaliada.", "",
           "## Lista por onda", "",
           "Todas as tarefas atuais têm uma issue. Já há implementação de cadastro e presença na Onda 1 e de coleta de localização na Onda 2. As issues das duas ondas registram esse andamento e o trabalho restante; publicar uma tarefa não inicia sua execução.",
           "Referências antigas ficam identificadas como histórico e fora da lista atual. T76/T77 foram retiradas junto da navegação externa.", ""]
    for wave in (1, 2):
        out += [f"### Onda {wave}", "", "| ID / issue | Resultado da tarefa | Implementação | Validação | Próxima ação |", "|---|---|---|---|---|"]
        for tid, task in tasks.items():
            if task[1] != wave: continue
            item, spec = status[tid], specs[tid]
            label = f"[{tid} · #{task[7]}]({REPO}/issues/{task[7]})" if isinstance(task[7], int) else f"[{tid}](08-especificacoes-mvp.md#{tid.lower()})"
            row = [label, spec["outcome"], item["implementationStatus"], item["validationStatus"], item["nextAction"]]
            out.append("| " + " | ".join(str(x).replace("|", "\\|").replace("\n", " ") for x in row) + " |")
        out.append("")
    out += ["## Manutenção", "",
            "Os dados ficam em `docs/backlog/tarefas.json`, `acompanhamento.json` e `criterios.json`.",
            "Preserve os IDs. Atualize os dados e a issue no mesmo trabalho; gere este documento com `python3 scripts/backlog.py`.",
            "O programa controla o objetivo ativo e os gates. Não marque todas as tarefas como concluídas por fechar um card técnico agregado.",
            "BACKLOG.xlsx conserva duas abas e oito colunas; os números de issue devem ser reais. Para atualizar a planilha, siga o guia de contribuição.", ""]
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    outputs = {"docs/07-como-ler-o-backlog.md": render(),
               "docs/08-especificacoes-mvp.md": render_specifications()}
    for name, content in outputs.items():
        target = ROOT / name
        if args.check:
            if target.read_text(encoding="utf-8") != content:
                raise SystemExit(f"{name} diverge dos dados; execute python3 scripts/backlog.py")
        else:
            target.write_text(content, encoding="utf-8")
    if args.check:
        print("Backlog: IDs, critérios, issues e documentos coerentes")


def render_specifications():
    _, tasks, status, specs = load()
    out = ["# Especificações das tarefas", "",
           "Cada tarefa define problema, resultado, limites e prova. Os critérios devem ser revisados com o grupo;",
           "nenhuma caixa marcada é inferida do estado de implementação. Use a issue para registrar a revisão.", "",
           "A navegação externa foi retirada do MVP. T76/T77 permanecem somente no histórico.",
           "T72/T73/T74 são propostas de escopo e exigem decisão antes da execução.", "",
           "[Voltar ao backlog](07-como-ler-o-backlog.md)", ""]
    for tid, task in tasks.items():
        s, item = specs[tid], status[tid]
        issue = f"[Issue #{task[7]}]({REPO}/issues/{task[7]})" if isinstance(task[7], int) else "Issue a criar"
        out += [f'<a id="{tid.lower()}"></a>', f"## {tid} — {s['shortTitle']}", "",
                f"{task[2]} · Onda {task[1]} · {issue}", "",
                f"**Problema:** {s['problem']}", "", f"**Resultado esperado:** {s['outcome']}", "",
                f"**Situação:** {item['implementationStatus']} · {item['validationStatus']}. {item.get('validationNote', '')}", "",
                "**Inclui:**", "", *[f"- {v}" for v in s['inScope']], "",
                "**Fora desta tarefa:**", "", *[f"- {v}" for v in s['outOfScope']], "",
                "**Critérios de conclusão:**", "", *[f"- [ ] {v}" for v in s['acceptanceCriteria']], "",
                f"**Como testar ({s['proofType']}):**", "", *[f"- {v}" for v in s['verification']], "",
                "**Dependências:**", "", *[f"- {v}" for v in s['prerequisites']], "",
                f"**Decisão pendente:** {s.get('decisionPending') or 'Nenhuma adicional identificada.'}", "",
                f"**Próxima ação:** {item['nextAction']}", ""]
        paths = list(dict.fromkeys(item.get('sourcePaths', []) + item.get('documentationPaths', [])))[:4]
        if paths:
            out += ["**Onde conferir:**", "", *[f"- `{path}`" for path in paths], ""]
    return "\n".join(out)


if __name__ == "__main__":
    main()
