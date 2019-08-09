/*******************************************************************************
 * Copyright (c) 2018 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered.intermediate;

import java.util.List;

import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LNode.NodeType;
import org.eclipse.elk.alg.layered.options.InternalProperties;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopComponent;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopNode;
import org.eclipse.elk.alg.layered.p5edges.loops.SelfLoopPort;
import org.eclipse.elk.alg.layered.p5edges.loops.calculators.SelfLoopComponentDependencyGraphCalculator;
import org.eclipse.elk.alg.layered.p5edges.loops.calculators.SelfLoopLevelCalculator;
import org.eclipse.elk.alg.layered.p5edges.loops.position.FixedOrderSelfLoopPortPositioner;
import org.eclipse.elk.alg.layered.p5edges.loops.position.FixedSideSelfLoopPortPositioner;
import org.eclipse.elk.alg.layered.p5edges.loops.position.FreePortsSelfLoopPortPositioner;
import org.eclipse.elk.alg.layered.p5edges.loops.position.ISelfLoopPortPositioner;
import org.eclipse.elk.alg.layered.p5edges.loops.position.SelfLoopNodePortRestorator;
import org.eclipse.elk.core.alg.ILayoutProcessor;
import org.eclipse.elk.core.util.IElkProgressMonitor;

/**
 * The ports of the node are reordered to provide the best possible self-loop placement.
 * 
 * <dl>
 * <dt>Preconditions:</dt>
 * <dt>Postconditions:</dt>
 * <dd>All ports are ordered over the node sides for the best possible self-loop placement.</dd>
 * <dd>Hidden ports are added back to their node.</dd>
 * <dd>The {@link SelfLoopNode} contains the {@link SelfLoopPort} corresponding to the node's port.</dd>
 * <dd>Each {@link SelfLoopPort} contains information about the routing direction and the height of their self
 * loops.</dd>
 * <dt>Slots:</dt>
 * <dd>Before phase 4.</dd>
 * <dt>Same-slot dependencies:</dt>
 * </dl>
 */
public final class SelfLoopPlacer implements ILayoutProcessor<LGraph> {

    @Override
    public void process(final LGraph layeredGraph, final IElkProgressMonitor monitor) {
        monitor.begin("Self-Loop positioning", 1);

        // for each node in each layer the self-loops are placed
        layeredGraph.getLayers().stream()
            .flatMap(layer -> layer.getNodes().stream())
            .filter(node -> node.getType() == NodeType.NORMAL)
            .filter(node -> node.hasProperty(InternalProperties.SELFLOOP_NODE_REPRESENTATION))
            .forEach(node -> processNode(node));

        monitor.done();
    }

    private void processNode(final LNode node) {
        SelfLoopNode slNode = node.getProperty(InternalProperties.SELFLOOP_NODE_REPRESENTATION);
        assert slNode != null;
        
        List<SelfLoopComponent> components = slNode.getSelfLoopComponents();

        // position the ports on the node
        ISelfLoopPortPositioner positioner = getPositioner(node);
        positioner.position(node);

        // calculate dependency graph for components
        SelfLoopComponentDependencyGraphCalculator.calculateComponentDependecies(slNode);
        // calculate an order for each edge in the components
        SelfLoopComponentDependencyGraphCalculator.calculateEdgeDependecies(components);

        // assign levels
        SelfLoopLevelCalculator.calculatePortLevels(slNode);
        SelfLoopLevelCalculator.calculateEdgeOrders(components);
        SelfLoopLevelCalculator.calculateOpposingSegmentLevel(slNode);

        // readd ports to port
        if (node.getProperty(LayeredOptions.PORT_CONSTRAINTS).isPosFixed()) {
            // This might happen if port positions have been fixed for arranging a nested graph
            SelfLoopNodePortRestorator.restoreAndPlacePorts(node);
        } else {
            SelfLoopNodePortRestorator.restorePorts(node);
        }
    }

    /**
     * Determine the correct positioner for the given node subject to its port constraints.
     */
    private ISelfLoopPortPositioner getPositioner(final LNode node) {
        switch (node.getProperty(InternalProperties.ORIGINAL_PORT_CONSTRAINTS)) {
        case UNDEFINED:
        case FREE:
            return new FreePortsSelfLoopPortPositioner(
                    node.getProperty(LayeredOptions.EDGE_ROUTING_SELF_LOOP_DISTRIBUTION),
                    node.getProperty(LayeredOptions.EDGE_ROUTING_SELF_LOOP_ORDERING));

        case FIXED_SIDE:
            return new FixedSideSelfLoopPortPositioner(
                    node.getProperty(LayeredOptions.EDGE_ROUTING_SELF_LOOP_ORDERING));

        case FIXED_ORDER:
        case FIXED_POS:
        case FIXED_RATIO:
            return new FixedOrderSelfLoopPortPositioner();

        default:
            throw new AssertionError("Unknown port constraint");
        }

    }

}
