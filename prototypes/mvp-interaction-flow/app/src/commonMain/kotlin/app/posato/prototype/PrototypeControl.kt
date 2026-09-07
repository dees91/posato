package app.posato.prototype

import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeMoment
import app.posato.prototype.model.PrototypeScenario

sealed interface PrototypeControl {
    data class Moment(
        val moment: PrototypeMoment
    ) : PrototypeControl

    data class Scenario(
        val scenario: PrototypeScenario
    ) : PrototypeControl

    data class GuidedStep(
        val index: Int
    ) : PrototypeControl

    data class FreePlay(
        val action: PrototypeAction
    ) : PrototypeControl
}
