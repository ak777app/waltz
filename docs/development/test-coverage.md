# Test coverage (JaCoCo)

The root `pom.xml` binds `jacoco-maven-plugin` (`prepare-agent` + `report`) to every module.
Each module that runs tests writes `target/jacoco.exec` and an HTML/XML/CSV report under
`target/site/jacoco/`.

Because `waltz-integration-test` is a separate module, its execution data is produced in a
separate `jacoco.exec` from the unit-test modules. Reporting each exec file against the
production class directories lets us attribute coverage to unit tests vs integration tests.

## Reproducing the numbers

```
# 1. unit tests (waltz-common, waltz-model, waltz-data, waltz-service, waltz-web, ...)
mvn -s .build.settings.xml -Pbuild-postgres,waltz-postgres -pl '!waltz-integration-test' -Dskip.npm=true clean test
mkdir -p /tmp/cov/unit && for m in waltz-common waltz-model waltz-data waltz-service waltz-web; do cp $m/target/jacoco.exec /tmp/cov/unit/$m.exec; done

# 2. integration tests (module must run inside the reactor, so unit tests are re-run here)
mvn -s .build.settings.xml -Pbuild-postgres,waltz-postgres -pl waltz-integration-test -am -Dskip.npm=true test
cp waltz-integration-test/target/jacoco.exec /tmp/cov/it.exec

# 3. report each exec set against a module's classes (JaCoCo CLI, version matches ${jacoco.version})
CLI=~/.m2/repository/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar
mvn dependency:get -Dartifact=org.jacoco:org.jacoco.cli:0.8.12:jar:nodeps
for m in waltz-common waltz-model waltz-data waltz-service waltz-web; do
  java -jar $CLI report /tmp/cov/unit/*.exec --classfiles $m/target/classes --sourcefiles $m/src/main/java --html /tmp/cov/unit-$m --csv /tmp/cov/unit-$m.csv
  java -jar $CLI report /tmp/cov/it.exec       --classfiles $m/target/classes --sourcefiles $m/src/main/java --html /tmp/cov/it-$m   --csv /tmp/cov/it-$m.csv
done
```

## Results (commit at time of writing, JDK 17, Maven 3.6.3)

Test runs:

| Run | Tests | Failures | Skipped |
|---|---|---|---|
| Unit (`waltz-common` 630, `waltz-model` 33, `waltz-service` 159, `waltz-web` 35; `waltz-data`/`waltz-jobs`/`waltz-test-common` have no unit tests) | 857 | 0 | 0 |
| Integration (`waltz-integration-test`, 49 test classes, default `target.db=mssql` / `db.provider=embedded` -> H2 in MSSQL mode) | 245 | 0 | 4 |

Skipped integration tests (all `@Disabled`):

- `AttestationPreCheckServiceTest` (whole class) - "Problem with H2"
- `AttestationServiceTest.basicRetrieval`
- `ArchitectureRequiredChangeServiceTest` (whole class) - relies on identity insert
- `BulkUploadLegalEntityRelationshipServiceTest` (whole class) - `.status()` no longer on response

Playwright E2E tests in `waltz-test-common` are skipped by default (`playwright.skip=true`) and are
not included.

Re-running the integration suite with `-Dtarget.db=postgres -Ddb.provider=embedded` (Zonky embedded
Postgres 16) gives the same result to within 0.1% (`waltz-data` 32.6% vs 32.5% lines).

### Per-module line / branch coverage

| Module | Unit lines | Unit branches | Integration lines | Integration branches | Combined lines | Combined branches |
|---|---|---|---|---|---|---|
| waltz-common | 76.6% (779/1017) | 76.2% (244/320) | 39.3% (400/1017) | 30.3% (97/320) | 80.2% (816/1017) | 80.9% (259/320) |
| waltz-model | 28.8% (647/2249) | 26.3% (44/167) | 47.0% (1058/2249) | 31.7% (53/167) | 54.9% (1235/2249) | 49.1% (82/167) |
| waltz-data | 0.2% (44/20422) | 0.0% (0/1346) | 32.5% (6641/20422) | 24.0% (323/1346) | 32.5% (6641/20422) | 24.0% (323/1346) |
| waltz-service | 5.6% (726/12978) | 6.8% (142/2093) | 38.7% (5020/12978) | 26.1% (546/2093) | 42.0% (5453/12978) | 31.1% (651/2093) |
| waltz-web | 0.8% (67/8649) | 1.1% (4/374) | 0.0% (0/8649) | 0.0% (0/374) | 0.8% (67/8649) | 1.1% (4/374) |
| waltz-jobs | 0.0% (0/7346) | 0.0% (0/490) | 0.0% (0/7346) | 0.0% (0/490) | 0.0% (0/7346) | 0.0% (0/490) |
| waltz-schema (jOOQ generated) | 14.4% (2714/18904) | 0.2% (1/418) | 24.2% (4572/18904) | 29.7% (124/418) | 24.2% (4572/18904) | 29.7% (124/418) |

### Aggregates (hand-written code, i.e. excluding `waltz-schema`)

| Scope | Metric | Unit | Integration | Combined |
|---|---|---|---|---|
| All hand-written modules | lines | 4.3% (2263/52661) | 24.9% (13119/52661) | 27.0% (14212/52661) |
| All hand-written modules | branches | 9.1% (434/4790) | 21.3% (1019/4790) | 27.5% (1319/4790) |
| Core (`waltz-service` + `waltz-data`) | lines | 2.3% (770/33400) | 34.9% (11661/33400) | 36.2% (12094/33400) |
| Core (`waltz-service` + `waltz-data`) | branches | 4.1% (142/3439) | 25.3% (869/3439) | 28.3% (974/3439) |

### Unit vs integration attribution (executable lines)

| Module | Unit only | Integration only | Both | Uncovered |
|---|---|---|---|---|
| waltz-service | 3.3% (433) | 36.4% (4727) | 2.3% (293) | 58.0% (7525) |
| waltz-data | 0.0% (0) | 32.3% (6597) | 0.2% (44) | 67.5% (13781) |
| waltz-common | 40.9% (416) | 3.6% (37) | 35.7% (363) | 19.8% (201) |
| waltz-model | 7.9% (177) | 26.1% (588) | 20.9% (470) | 45.1% (1014) |
| waltz-web | 0.8% (67) | 0.0% (0) | 0.0% (0) | 99.2% (8582) |

Of the 12,094 core (`waltz-service` + `waltz-data`) lines that are covered at all, 93.6% (11,324)
are covered *only* by integration tests, 3.6% (433) only by unit tests and 2.8% (337) by both.

### Layer view

| Layer | Unit | Integration | Combined |
|---|---|---|---|
| DAO classes (`waltz-data` `*Dao`, 169 classes) | 0.0% (2/16802) | 32.4% (5450/16802) | 32.4% |
| Service classes (`waltz-service` `*Service`, 144 classes) | 0.2% (16/10159) | 41.8% (4248/10159) | 41.8% |

The `waltz-service` unit tests almost exclusively exercise stateless helpers (e.g.
`TaxonomyManagementUtilities`, `BulkTaxonomyItemParser`, `FlowClassificationRuleResolver`,
`ReportGridUtilities`) rather than `*Service` classes; the service and DAO layers are covered
essentially only by the DB-backed integration tests.
