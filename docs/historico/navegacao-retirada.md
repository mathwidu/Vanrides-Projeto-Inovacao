# Histórico da navegação externa — T76 e T77

Em 02/11/2026, o usuário retirou a navegação externa do MVP. T76 e T77 saem do backlog executável, do acompanhamento atual e das especificações atuais. Os IDs restantes permanecem iguais. Esta fotografia preserva integralmente as linhas de dados e os registros de acompanhamento e especificação da base ffc7f0d.

T76 tratava do link de navegação; T77 unia uma ordem de pontos já existente ao fornecimento para a navegação. Reduzir T77 a apenas ordenar pontos manteria uma nova tarefa sobre trabalho já coberto. A ordem dos pontos permanece em T40 (rotina), T43 (lista da partida) e T50 (progressão da aproximação), sem alteração de código ou de estado.

Os campos abaixo mantêm a redação e as propostas anteriores, inclusive decisões que deixaram de se aplicar ao recorte atual. Não são uma nova autorização, uma fila de execução ou uma pendência do MVP. As referências históricas de F01, T12/T13 e das issues antigas também não passam a representar trabalho atual.

## Registros anteriores completos

```json
{
  "status": "Retirado do MVP atual por decisão do usuário em 02/11/2026; registros abaixo são a fotografia anterior, não estados atuais.",
  "baseCommit": "ffc7f0d",
  "sourceDigests": {
    "backlog-data.json": "07d42d7afcec1467e0f704f69028e2a39eb8dc889842aa5860cb2e6feba400c1",
    "task-progress.json": "282a31f66dac4ff477d032debc869f3e1b681c2844125c53d330c55e8cab5c7d",
    "task-specifications.json": "b9d8ed5ddf3f728f8fe5c87e88b5c15b32595386f18d0062fc2eb42e7d03b697",
    "BACKLOG.xlsx": "19342dc3e156229b9c31983e6912de9e8993edab0411732a526c7c0123a02584",
    "publicacao/docs/07-como-ler-o-backlog.md": "6d4479e1faea9e6007dd93e4316213df03b9ae6051247a3a44f33bd846c5e43b",
    "publicacao/docs/08-especificacoes-mvp.md": "d78d66653153916e20f24fa933c85f5cb0c596a8f26de18f8af89ccf907709e2"
  },
  "records": [
    {
      "taskId": "T76",
      "backlog": [
        "T76",
        2,
        "F01",
        "Frontend",
        "Criar o link de navegação para o motorista a partir dos pontos e do sentido selecionados.",
        "M",
        null,
        null,
        [
          "080",
          "110"
        ],
        "AJUSTE PROPOSTO: navegação por link no recorte atual; não é API própria de otimização de rotas T12/T13."
      ],
      "progress": {
        "taskId": "T76",
        "title": "Criar o link de navegação para o motorista a partir dos pontos e do sentido selecionados.",
        "feature": "F01",
        "component": "Link de navegação do motorista",
        "implementationStatus": "Proposta de escopo",
        "codeStatus": "Planejado",
        "implementationNote": "Link de navegação do recorte atual não foi encontrado nos componentes WhatsApp inspecionados.",
        "validationStatus": "Pendente",
        "validationNote": "O link de início do GPS é diferente de link de navegação externa.",
        "sourcePaths": [
          "vanrides-app/lib/features/driver_mvp/presentation/driver_tracking_setup.dart",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverSetup.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java"
        ],
        "evidencePaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "planningPaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "nextAction": "Aprovar o ajuste e implementar o link a partir dos pontos selecionados.",
        "refs": [
          "VR-WA-080-DRIVER-ROUTINES",
          "VR-WA-110-RUN-TRACKING"
        ],
        "sourceVisibility": "Repositórios privados atuais; publicação acadêmica pendente.",
        "validationEvidencePaths": [],
        "documentationPaths": [],
        "evidenceRefs": [
          {
            "path": "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
            "kind": "Planejamento"
          },
          {
            "path": "program/cards/VR-WA-110-RUN-TRACKING.md",
            "kind": "Planejamento"
          }
        ]
      },
      "specification": {
        "taskId": "T76",
        "title": "Criar o link de navegação para o motorista a partir dos pontos e do sentido selecionados.",
        "shortTitle": "Proposta: criar link de navegação",
        "feature": "F01",
        "problem": "O motorista pode precisar abrir a navegação para os pontos escolhidos sem confundir isso com iniciar a coleta.",
        "outcome": "Se aprovado, o link leva à navegação externa correspondente aos pontos e sentido selecionados.",
        "inScope": [
          "Montagem e apresentação de link de navegação externa."
        ],
        "outOfScope": [
          "Criar navegador próprio, otimização de rotas ou tratar o link de GPS como navegação."
        ],
        "acceptanceCriteria": [
          "O link usa os pontos e o sentido explicitamente selecionados.",
          "A abertura solicita a navegação externa prevista e não inicia GPS por si só.",
          "Pontos indisponíveis ou não autorizados não são incluídos silenciosamente."
        ],
        "proofType": "Laboratório",
        "verification": [
          "Montar um link com pontos fictícios e conferir seu destino e sentido no aplicativo externo escolhido para o teste.",
          "Registro da jornada em ambiente separado, com componentes e versão usados e limite do canal simulado explícito."
        ],
        "prerequisites": [
          "Proposta aprovada, pontos autorizados e serviço de navegação externa definido para o ensaio."
        ],
        "decisionPending": "Confirmar a inclusão da navegação por link no recorte e o destino externo adotado; isso não foi aprovado por estes critérios.",
        "nextRequiredOutcome": "Aprovar o ajuste e implementar o link a partir dos pontos selecionados.",
        "sourcePaths": [
          "vanrides-app/lib/features/driver_mvp/presentation/driver_tracking_setup.dart",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingService.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverSetup.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java"
        ],
        "evidencePaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "planningPaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ]
      }
    },
    {
      "taskId": "T77",
      "backlog": [
        "T77",
        2,
        "F01",
        "Backend",
        "Guardar a ordem dos pontos da rotina e fornecer os dados autorizados para a navegação.",
        "M",
        null,
        null,
        [
          "080",
          "110"
        ],
        "AJUSTE PROPOSTO: F01 era Onda 3; não clonar nem executar vanrides-maps-platform."
      ],
      "progress": {
        "taskId": "T77",
        "title": "Guardar a ordem dos pontos da rotina e fornecer os dados autorizados para a navegação.",
        "feature": "F01",
        "component": "Ordem dos pontos para navegação",
        "implementationStatus": "Proposta de escopo",
        "codeStatus": "Parcial",
        "implementationNote": "Ordem dos pontos está implementada; fornecimento para navegação depende do link futuro.",
        "validationStatus": "Laboratório",
        "validationNote": "Teste stopOrderIsExactPermutationOfApprovedActiveMembers e snapshot registrados.",
        "sourcePaths": [
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java",
          "vanrides-backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDriverRoutinesIntegrationTest.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaRunPreparation.java",
          "vanrides-backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaRunPreparationIntegrationTest.java"
        ],
        "evidencePaths": [
          "program/evidence/VR-WA-080-DRIVER-ROUTINES.md",
          "program/evidence/VR-WA-110-RUN-TRACKING.md",
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "planningPaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "nextAction": "Aprovar o ajuste e integrar os dados à navegação.",
        "refs": [
          "VR-WA-080-DRIVER-ROUTINES",
          "VR-WA-110-RUN-TRACKING"
        ],
        "sourceVisibility": "Repositórios privados atuais; publicação acadêmica pendente.",
        "validationEvidencePaths": [
          "program/evidence/VR-WA-080-DRIVER-ROUTINES.md",
          "program/evidence/VR-WA-110-RUN-TRACKING.md"
        ],
        "documentationPaths": [],
        "evidenceRefs": [
          {
            "path": "program/evidence/VR-WA-080-DRIVER-ROUTINES.md",
            "kind": "Validação documentada"
          },
          {
            "path": "program/evidence/VR-WA-110-RUN-TRACKING.md",
            "kind": "Validação documentada"
          },
          {
            "path": "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
            "kind": "Planejamento"
          },
          {
            "path": "program/cards/VR-WA-110-RUN-TRACKING.md",
            "kind": "Planejamento"
          }
        ]
      },
      "specification": {
        "taskId": "T77",
        "title": "Guardar a ordem dos pontos da rotina e fornecer os dados autorizados para a navegação.",
        "shortTitle": "Proposta: fornecer ordem dos pontos",
        "feature": "F01",
        "problem": "A navegação precisa respeitar uma ordem de pontos escolhida e permitida.",
        "outcome": "Se aprovado, os dados de ordem existentes alimentam a navegação sem misturar rotinas.",
        "inScope": [
          "Guardar ordem explícita dos pontos e disponibilizar os dados autorizados."
        ],
        "outOfScope": [
          "Escolher automaticamente uma rota otimizada ou reescrever a lista de uma partida iniciada."
        ],
        "acceptanceCriteria": [
          "A ordem contém os pontos aprovados da rotina selecionada, sem repetição ou ausência indevida.",
          "Outra pessoa ou rotina não altera a ordem sem permissão.",
          "A alteração posterior preserva a lista da partida já iniciada e os dados fornecidos respeitam o sentido escolhido."
        ],
        "proofType": "Teste automatizado",
        "verification": [
          "Revisar a ordenação existente, tentar ponto não aprovado e outra rotina e comparar a lista após iniciar uma viagem.",
          "Relatório dos cenários automatizados, com versão avaliada, resultado e falhas preservadas."
        ],
        "prerequisites": [
          "Proposta aprovada e participantes/pontos ativos e aprovados da rotina disponíveis."
        ],
        "decisionPending": "Confirmar a inclusão desse apoio à navegação na Onda 2, mantendo o que já existe de ordenação identificado.",
        "nextRequiredOutcome": "Aprovar o ajuste e integrar os dados à navegação.",
        "sourcePaths": [
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaDriverMutations.java",
          "vanrides-backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaDriverRoutinesIntegrationTest.java",
          "vanrides-backend/src/main/java/com/vanrides/vanrides/whatsapp/commands/WaRunPreparation.java",
          "vanrides-backend/src/test/java/com/vanrides/vanrides/whatsapp/transport/WaRunPreparationIntegrationTest.java"
        ],
        "evidencePaths": [
          "program/evidence/VR-WA-080-DRIVER-ROUTINES.md",
          "program/evidence/VR-WA-110-RUN-TRACKING.md",
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ],
        "planningPaths": [
          "program/cards/VR-WA-080-DRIVER-ROUTINES.md",
          "program/cards/VR-WA-110-RUN-TRACKING.md"
        ]
      }
    }
  ]
}
```
