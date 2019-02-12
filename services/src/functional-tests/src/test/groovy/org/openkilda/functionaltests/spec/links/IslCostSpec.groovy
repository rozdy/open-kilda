package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.northbound.dto.links.LinkPropsDto

import org.springframework.beans.factory.annotation.Value
import spock.lang.Shared
import spock.lang.Unroll

class IslCostSpec extends BaseSpecification {

    @Shared
    @Value('${isl.cost.when.port.down}')
    int islCostWhenPortDown

    def "ISL cost is increased due to bringing port down on a switch (ISL cost < isl.cost.when.port.down)"() {
        given: "Active forward and reverse ISLs with equal cost"
        def isl = topology.islsForActiveSwitches.first()
        def reverseIsl = islUtils.reverseIsl(isl)

        int islCost = database.getIslCost(isl)
        assert database.getIslCost(reverseIsl) == islCost

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        database.getIslCost(isl) == islCostWhenPortDown + islCost
        database.getIslCost(reverseIsl) == islCostWhenPortDown + islCost

        and: "Bring port up on the source switch and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()
    }

    @Unroll
    def "ISL cost is NOT increased due to bringing port down on a switch \
(ISL cost #condition isl.cost.when.port.down)"() {
        given: "Active forward and reverse ISLs"
        def isl = topology.islsForActiveSwitches.first()
        def reverseIsl = islUtils.reverseIsl(isl)

        and: "Forward ISL has cost #condition isl.cost.when.port.down"
        def linkProps = [new LinkPropsDto(isl.srcSwitch.dpId.toString(), isl.srcPort, isl.dstSwitch.dpId.toString(),
                isl.dstPort, ["cost": cost.toString()])]
        northbound.updateLinkProps(linkProps)

        int islCost = database.getIslCost(isl)
        int reverseIslCost = database.getIslCost(reverseIsl)
        assert islCost != reverseIslCost

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward ISL after bringing port down is NOT increased"
        database.getIslCost(isl) == islCost

        and: "Cost of reverse ISL after bringing port down is increased"
        database.getIslCost(reverseIsl) == islCostWhenPortDown + reverseIslCost

        and: "Bring port up on the source switch, delete link props and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        cost                    | condition
        islCostWhenPortDown     | "=="
        islCostWhenPortDown + 1 | ">"
    }

    def "ISL cost is NOT increased due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort && !it.bfd
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)
        def reverseIsl = islUtils.reverseIsl(isl)

        int islCost = database.getIslCost(isl)
        assert database.getIslCost(reverseIsl) == islCost

        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.aswitch, isl.aswitch.reversed]
        lockKeeper.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward and reverse ISLs after failing connection is not increased"
        database.getIslCost(isl) == islCost
        database.getIslCost(reverseIsl) == islCost

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }
    }

    def "ISL(not BFD) is NOT FAILED earlier than discoveryTimeout is exceeded when connection is lost(not port down)"() {
        given: "ISL going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort && !it.bfd
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)

        assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        double waitTime = discoveryTimeout - (discoveryTimeout * 0.2)
        double interval = discoveryTimeout * 0.2
        def ruleToRemove = [isl.aswitch]
        def reverseIsl = islUtils.reverseIsl(isl)

        when: "Remove a one-way flow on an a-switch for simulating lost connection(not port down)"
        lockKeeper.removeFlows(ruleToRemove)

        then: "Status of ISL is not changed to FAILED until discoveryTimeout is exceeded"
        Wrappers.timedLoop(waitTime) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
            sleep((interval * 1000).toLong())
        }

        and: "Status of ISL is changed to FAILED when discoveryTimeout is exceeded"
        /**
         * actualState shows real state of ISL and this value is taken from DB
         * also it allows to understand direction where issue has appeared
         * e.g. in our case we've removed a one-way flow(A->B)
         * the other one(B->A) still exists
         * afterward the actualState of ISL on A side is equal to FAILED
         * and on B side is equal to DISCOVERED
         * */
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().actualState == IslChangeType.DISCOVERED
        }

        when: "Add the removed one-way flow rule for restoring topology"
        lockKeeper.addFlows(ruleToRemove)

        then: "ISL is discovered back"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().actualState == IslChangeType.DISCOVERED
        }
    }
}
