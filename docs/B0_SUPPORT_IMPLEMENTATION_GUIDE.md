# Guide d'implémentation du support B0 dans powsybl-core

## Contexte

La susceptance homopolaire B0 (capacitance shunt en séquence zéro) a un impact significatif sur les calculs de court-circuit monophasés pour les lignes avec forte capacitance (c0 > 500 nF/km).

**Impact mesuré:**
- c0 < 250 nF/km: écart < 6% (négligeable)
- c0 = 500 nF/km: écart ~12% (modéré)
- c0 = 1000 nF/km: écart ~25% (critique)
- c0 = 2000 nF/km: écart ~40% (très critique, défaut 1ph > défaut 3ph)

## Solution actuelle (workaround)

Dans powsybl-open-sc, nous calculons une **impédance effective** qui combine l'impédance série avec l'admittance shunt en parallèle:

```java
// Z0_effective = 1 / (1/Z0_series + jB0)
double z0MagSq = r0Series * r0Series + x0Series * x0Series;
double g0Series = r0Series / z0MagSq;
double b0Series = -x0Series / z0MagSq;
double gTotal = g0Series;
double bTotal = b0Series + b0;
double yMagSq = gTotal * gTotal + bTotal * bTotal;
double r0Effective = gTotal / yMagSq;
double x0Effective = -bTotal / yMagSq;
```

**Limitations:**
- Approximation simplifiée (modèle série||shunt au lieu de pi-model complet)
- Ne correspond pas exactement au modèle pandapower
- Perte d'information sur la valeur originale de B0

## Solution recommandée: Extension powsybl-core

### 1. Modifier l'interface LineFortescueAdder

**Fichier:** `powsybl-core/iidm/iidm-api/src/main/java/com/powsybl/iidm/network/extensions/LineFortescueAdder.java`

```java
public interface LineFortescueAdder extends ExtensionAdder<Line, LineFortescue> {

    LineFortescueAdder withRz(double rz);

    LineFortescueAdder withXz(double xz);

    // NOUVEAU: Ajouter support de la susceptance homopolaire
    /**
     * Set the zero-sequence shunt susceptance in Siemens.
     * This represents the capacitive charging current in the zero-sequence network.
     * For overhead lines, this is typically negligible (< 0.001 S).
     * For underground cables, this can be significant (0.001-0.01 S).
     *
     * @param bz zero-sequence susceptance in Siemens (positive for capacitive)
     * @return this adder
     */
    LineFortescueAdder withBz(double bz);

    // NOUVEAU: Conductance homopolaire (généralement 0 sauf pertes diélectriques)
    /**
     * Set the zero-sequence shunt conductance in Siemens.
     * This represents dielectric losses. Typically zero or very small.
     *
     * @param gz zero-sequence conductance in Siemens
     * @return this adder
     */
    LineFortescueAdder withGz(double gz);

    @Override
    LineFortescue add();
}
```

### 2. Modifier la classe LineFortescue

**Fichier:** `powsybl-core/iidm/iidm-extensions/src/main/java/com/powsybl/iidm/network/extensions/LineFortescue.java`

```java
public interface LineFortescue extends Extension<Line> {

    double getRz();
    LineFortescue setRz(double rz);

    double getXz();
    LineFortescue setXz(double xz);

    // NOUVEAU
    /**
     * Get the zero-sequence shunt susceptance in Siemens.
     * @return susceptance in S, or 0.0 if not set
     */
    double getBz();

    /**
     * Set the zero-sequence shunt susceptance in Siemens.
     * @param bz susceptance in S
     * @return this extension
     */
    LineFortescue setBz(double bz);

    /**
     * Check if zero-sequence susceptance is defined.
     * @return true if Bz is set and non-zero
     */
    boolean hasBz();

    // NOUVEAU
    double getGz();
    LineFortescue setGz(double gz);
    boolean hasGz();
}
```

### 3. Modifier l'implémentation LineFortescueImpl

**Fichier:** `powsybl-core/iidm/iidm-extensions/src/main/java/com/powsybl/iidm/network/extensions/LineFortescueImpl.java`

```java
class LineFortescueImpl extends AbstractExtension<Line> implements LineFortescue {

    private double rz;
    private double xz;
    private double bz;  // NOUVEAU
    private double gz;  // NOUVEAU

    LineFortescueImpl(Line line, double rz, double xz, double bz, double gz) {
        super(line);
        this.rz = rz;
        this.xz = xz;
        this.bz = bz;
        this.gz = gz;
    }

    // Getters/setters existants...

    @Override
    public double getBz() {
        return bz;
    }

    @Override
    public LineFortescue setBz(double bz) {
        this.bz = bz;
        return this;
    }

    @Override
    public boolean hasBz() {
        return !Double.isNaN(bz) && Math.abs(bz) > 1e-10;
    }

    @Override
    public double getGz() {
        return gz;
    }

    @Override
    public LineFortescue setGz(double gz) {
        this.gz = gz;
        return this;
    }

    @Override
    public boolean hasGz() {
        return !Double.isNaN(gz) && Math.abs(gz) > 1e-10;
    }
}
```

### 4. Mettre à jour les sérialiseurs XML/JSON

**Fichiers à modifier:**
- `powsybl-core/iidm/iidm-xml-converter/src/main/java/com/powsybl/iidm/xml/LineFortescueXml.java`
- Ajouter attributs `bz` et `gz` dans le schéma XSD
- Ajouter support JSON si applicable

**Exemple XML:**
```xml
<iidm:lineFortescue rz="3.66" xz="5.04" bz="0.009425" gz="0.0"/>
```

### 5. Adapter HomopolarModel dans powsybl-open-sc

**Fichier:** `powsybl-open-sc/sc-implementation/src/main/java/com/powsybl/sc/util/extensions/HomopolarModel.java`

Ligne ~147-149, au lieu de modéliser juste bom = 0:

```java
if (scLine.hasB0()) {
    homopolarExtension.gom = scLine.getG0();  // Utiliser gz si disponible
    homopolarExtension.bom = scLine.getB0();
}
```

### 6. Tests de validation

Créer des tests pour vérifier:

1. **Compatibilité ascendante:** Les réseaux sans Bz/Gz doivent fonctionner comme avant
2. **Sérialisation:** Import/export XML/JSON préserve Bz/Gz
3. **Calculs:** Validation contre pandapower pour c0 = 100, 500, 1000, 2000 nF/km
4. **Pi-model:** Vérifier que le modèle pi complet est utilisé (B0/2 à chaque extrémité)

**Test de référence:**
```java
@Test
void testLineFortescueWithBz() {
    Network network = // ...
    Line line = network.getLine("L1");

    line.newExtension(LineFortescueAdder.class)
        .withRz(3.66)
        .withXz(5.04)
        .withBz(0.009425)  // NOUVEAU
        .withGz(0.0)       // NOUVEAU
        .add();

    LineFortescue ext = line.getExtension(LineFortescue.class);
    assertEquals(0.009425, ext.getBz(), 1e-6);
    assertTrue(ext.hasBz());
}
```

## Formules de calcul

### Conversion c0 → B0

Pour une ligne de longueur L (km) avec capacitance c0 (nF/km):

```
C0_total = c0 × 10⁻⁹ × L  [Farads]
B0 = 2πfC0              [Siemens]
```

À 50 Hz:
```
B0 = 314.159 × c0 × 10⁻⁹ × L  [S]
```

### Modèle pi pour Z0

Pour une ligne avec:
- Impédance série: Z0 = R0 + jX0
- Admittance shunt: Y0 = G0 + jB0

Le modèle pi a:
- Y0/2 en shunt à chaque extrémité
- Z0 en série au centre

Matrice ABCD:
```
A = 1 + Z0×Y0/2
B = Z0
C = Y0×(1 + Z0×Y0/4)
D = 1 + Z0×Y0/2
```

## Migration

### Phase 1: Ajout rétrocompatible
1. Ajouter `withBz()` et `withGz()` avec valeur par défaut 0
2. Implémenter dans powsybl-core (version N+1)
3. Release et test sur réseaux existants

### Phase 2: Utilisation dans powsybl-open-sc
1. Mettre à jour dépendance vers powsybl-core N+1
2. Remplacer calcul d'impédance effective par appel à `withBz()`
3. Mettre à jour HomopolarModel pour utiliser Bz

### Phase 3: Validation
1. Tests de régression sur IEEE-14, IEEE-118
2. Validation pandapower sur câbles HT (c0 > 500 nF/km)
3. Documentation utilisateur

## Estimation d'effort

- **powsybl-core:** 3-5 jours
  - Modification interfaces: 1j
  - Implémentation: 1j
  - Sérialisation XML/JSON: 1j
  - Tests: 1-2j

- **powsybl-open-sc:** 2-3 jours
  - Adaptation HomopolarModel: 1j
  - Tests et validation: 1-2j

- **Documentation:** 1 jour

**Total estimé:** 6-9 jours développeur

## Références

- IEC 60909-0:2016 - Section sur impédances homopolaires
- IEEE Std 399-1997 (Brown Book) - Chapitre sur séquences
- Pandapower: `pd2ppc_zero.py` ligne 522-531
- OpenSC issue: COUR-6 (validation pandapower single-phase faults)

## Auteur

Équipe RTE-i / Letsco
Date: 2026-01-08
