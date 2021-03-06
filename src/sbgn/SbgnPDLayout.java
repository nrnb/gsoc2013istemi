package org.ivis.layout.sbgn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.ivis.layout.LEdge;
import org.ivis.layout.LGraph;
import org.ivis.layout.LNode;
import org.ivis.layout.cose.CoSELayout;
import org.ivis.layout.fd.FDLayoutConstants;
import org.ivis.layout.util.MemberPack;
import org.ivis.layout.util.RectProc;
import org.ivis.util.RectangleD;

/**
 * This class implements the layout process of SBGN notation.
 * 
 * @author Istemi Bahceci
 * 
 *         Copyright: i-Vis Research Group, Bilkent University, 2007 - present
 */
public class SbgnPDLayout extends CoSELayout
{
	// VARIABLES SECTION

	/**
	 * For remembering contents of a complex.
	 */
	Map<SbgnPDNode, LGraph> childGraphMap;

	/**
	 * Used during Tiling.
	 */
	Map<SbgnPDNode, MemberPack> memberPackMap;

	/**
	 * This list stores the complex molecules as a result of DFS. The first
	 * element corresponds to the deep-most node.
	 */
	LinkedList<SbgnPDNode> complexOrder;

	/**
	 * This parameter indicates the chosen compaction method.
	 */
	private DefaultCompactionAlgorithm compactionMethod;

	// METHODS SECTION

	/**
	 * The constructor creates and associates with this layout a new graph
	 * manager as well. No tiling performs CoSE Layout.
	 * 
	 * @param compactionMethod
	 *            - SbgnPDConstants.TILING, SbgnPDConstants.POLYOMINO_PACKING
	 */
	public SbgnPDLayout()
	{
		compactionMethod = DefaultCompactionAlgorithm.POLYOMINO_PACKING;

		childGraphMap = new HashMap<SbgnPDNode, LGraph>();
		complexOrder = new LinkedList<SbgnPDNode>();

		if (compactionMethod == DefaultCompactionAlgorithm.TILING)
			memberPackMap = new HashMap<SbgnPDNode, MemberPack>();
	}

	/**
	 * This method creates a new node associated with the input view node.
	 */
	public LNode newNode(Object vNode)
	{
		return new SbgnPDNode(this.graphManager, vNode);
	}

	/**
	 * This method creates a new edge associated with the input view edge.
	 */
	public LEdge newEdge(Object vEdge)
	{
		return new SbgnPDEdge(null, null, vEdge);
	}

	/**
	 * This method performs layout on constructed l-level graph. It returns true
	 * on success, false otherwise.
	 */
	public boolean layout()
	{
		boolean b = false;

		DFSComplex();

		b = super.layout();

		repopulateComplexes();
		// calculateFullnessOfComplexes();

		System.out.println("SbgnPD Layout has finished after "
				+ totalIterations + " iterations");
		return b;
	}

	@Override
	public void moveNodes()
	{
		super.moveNodes();
	}

	@Override
	public void calcSpringForces()
	{
		super.calcSpringForces();
	}

	/**
	 * @Override This method performs the actual layout on the l-level compound
	 *           graph. An update() needs to be called for changes to be
	 *           propogated to the v-level compound graph.
	 */
	public void runSpringEmbedder()
	{
		do
		{
			this.totalIterations++;
			if (this.totalIterations
					% FDLayoutConstants.CONVERGENCE_CHECK_PERIOD == 0)
			{
				if (this.isConverged())
				{
					break;
				}

				this.coolingFactor = this.initialCoolingFactor
						* ((this.maxIterations - this.totalIterations) / (double) this.maxIterations);

				// this.updateAnnealingProbability();
			}
			
			this.totalDisplacement = 0;

			this.graphManager.updateBounds();
			this.calcSpringForces();
			this.calcRepulsionForces();
			this.calcGravitationalForces();
			
			this.calcRelativityConstraintForces();

			this.moveNodes();
			this.animate();
		}
		while (this.totalIterations < this.maxIterations);
		
		this.graphManager.updateBounds();
	}

	/**
	 * This method is for updating orientations of nodes 
	 **/
	private void calcRelativityConstraintForces()
	{		
		//check for any node that does not have type information
		for (int i = 0; i < this.getAllNodes().length; i++)
		{
			SbgnPDNode node = (SbgnPDNode) this.getAllNodes()[i];
			
			if(node.type == null)
			{
				System.out.println("Be careful. There are a number of nodes that do not have type information.");
				break;
			}
		}
		
		for (int i = 0; i < this.getAllNodes().length; i++)
		{
			SbgnPDNode node = (SbgnPDNode) this.getAllNodes()[i];
			
			if(node.type.equals(SbgnPDConstants.PROCESS))
				node.calcRelativityForce();
		}
	}

	/**
	 * This method searched unmarked complex nodes recursively, because they may
	 * contain complex children. After the order is found, child graphs of each
	 * complex node are cleared.
	 */
	public void DFSComplex()
	{
		for (Object o : getAllNodes())
		{
			if (!(o instanceof SbgnPDNode) || !((SbgnPDNode) o).isComplex())
				continue;

			SbgnPDNode comp = (SbgnPDNode) o;

			// complex is found, recurse on it until no visited complex remains.
			if (!comp.visited)
				DFSVisitComplex(comp);
		}

		// clear each complex
		for (SbgnPDNode o : complexOrder)
			clearComplex(o);

		getGraphManager().resetAllNodes();
		getGraphManager().resetAllNodesToApplyGravitation();
		getGraphManager().resetAllEdges();
	}

	/**
	 * This method recurses on the complex objects. If a node does not contain
	 * any complex nodes or all the nodes in the child graph is already marked,
	 * it is reported. (Depth first)
	 * 
	 */
	public void DFSVisitComplex(SbgnPDNode node)
	{
		if (node.getChild() != null)
		{
			for (Object n : node.getChild().getNodes())
			{
				SbgnPDNode sbgnChild = (SbgnPDNode) n;
				DFSVisitComplex(sbgnChild);
			}
		}

		if (node.isComplex() && !containsUnmarkedComplex(node))
		{
			complexOrder.add(node);
			node.visited = true;
			return;
		}
	}

	/**
	 * This method checks if the given node contains any unmarked complex nodes
	 * in its child graph.
	 * 
	 * @return true - if there are unmarked complex nodes false - otherwise
	 */
	public boolean containsUnmarkedComplex(SbgnPDNode comp)
	{
		if (comp.getChild() == null)
			return false;
		else
		{
			for (Object child : comp.getChild().getNodes())
			{
				SbgnPDNode sbgnChild = (SbgnPDNode) child;

				if (sbgnChild.isComplex() && !sbgnChild.visited)
					return true;
			}
			return false;
		}
	}

	/**
	 * This method applies polyomino packing on the child graph of a complex
	 * member and then..
	 * 
	 * @param comp
	 */
	private void clearComplex(SbgnPDNode comp)
	{
		MemberPack pack = null;
		LGraph childGr = comp.getChild();
		childGraphMap.put(comp, childGr);

		if (compactionMethod == DefaultCompactionAlgorithm.POLYOMINO_PACKING)
		{
			applyPolyomino(comp);
		}
		else if (compactionMethod == DefaultCompactionAlgorithm.TILING)
		{
			pack = new MemberPack(childGr);
			memberPackMap.put(comp, pack);
		}

		getGraphManager().getGraphs().remove(childGr);
		comp.setChild(null);

		if (compactionMethod == DefaultCompactionAlgorithm.TILING)
		{
			comp.setWidth(pack.getWidth());
			comp.setHeight(pack.getHeight());
		}

		// Redirect the edges of complex members to the complex.
		if (childGr != null)
		{
			for (Object ch : childGr.getNodes())
			{
				SbgnPDNode chNd = (SbgnPDNode) ch;

				for (Object obj : new ArrayList(chNd.getEdges()))
				{
					LEdge edge = (LEdge) obj;
					if (edge.getSource() == chNd)
					{
						chNd.getEdges().remove(edge);
						edge.setSource(comp);
						comp.getEdges().add(edge);
					}
					else if (edge.getTarget() == chNd)
					{
						chNd.getEdges().remove(edge);
						edge.setTarget(comp);
						comp.getEdges().add(edge);
					}
				}
			}
		}
	}

	/**
	 * This method tiles the given list of nodes by using polyomino packing
	 * algorithm.
	 */
	private void applyPolyomino(SbgnPDNode parent)
	{
		RectangleD r;
		LGraph childGr = parent.getChild();

		if (childGr == null)
		{
			System.out.println("Child graph is empty (Polyomino)");
		}
		else
		{
			// packing takes the input as an array. put the members in an array.
			SbgnPDNode[] mpArray = new SbgnPDNode[childGr.getNodes().size()];
			for (int i = 0; i < childGr.getNodes().size(); i++)
			{
				SbgnPDNode s = (SbgnPDNode) childGr.getNodes().get(i);
				mpArray[i] = s;
			}

			// pack rectangles
			RectProc.packRectanglesMino(
					SbgnPDConstants.COMPLEX_MEM_HORIZONTAL_BUFFER,
					mpArray.length, mpArray);

			// apply compaction
			Compaction c = new Compaction(
					(ArrayList<SbgnPDNode>) childGr.getNodes());
			c.perform();

			// get the resulting rectangle and set parent's (complex) width &
			// height
			r = calculateBounds(true,
					(ArrayList<SbgnPDNode>) childGr.getNodes());

			parent.setWidth(r.getWidth());
			parent.setHeight(r.getHeight());
		}
	}

	/**
	 * Reassigns the complex content. The outermost complex is placed first.
	 */
	protected void repopulateComplexes()
	{
		for (int i = complexOrder.size() - 1; i >= 0; i--)
		{
			SbgnPDNode comp = complexOrder.get(i);
			LGraph chGr = childGraphMap.get(comp);

			// repopulate the complex
			comp.setChild(chGr);

			// if the child graph is not null, adjust the positions of members
			if (chGr != null)
			{
				// adjust the positions of the members
				if (compactionMethod == DefaultCompactionAlgorithm.POLYOMINO_PACKING)
				{
					RectangleD rect = calculateBounds(false,
							(ArrayList<SbgnPDNode>) chGr.getNodes());

					int differenceX = (int) (rect.x - comp.getLeft());
					int differenceY = (int) (rect.y - comp.getTop());

					for (int j = 0; j < chGr.getNodes().size(); j++)
					{
						SbgnPDNode s = (SbgnPDNode) chGr.getNodes().get(j);
						s.setLocation(s.getLeft() - differenceX
								+ SbgnPDConstants.COMPLEX_MEM_MARGIN,
								s.getTop() - differenceY
										+ SbgnPDConstants.COMPLEX_MEM_MARGIN);
					}
					getGraphManager().getGraphs().add(chGr);
				}
				else if (compactionMethod == DefaultCompactionAlgorithm.TILING)
				{
					getGraphManager().getGraphs().add(chGr);

					MemberPack pack = memberPackMap.get(comp);
					pack.adjustLocations(comp.getLeft(), comp.getTop());
				}
			}
		}

		// reset
		getGraphManager().resetAllNodes();
		getGraphManager().resetAllNodesToApplyGravitation();
		getGraphManager().resetAllEdges();
	}

	/**
	 * This method returns the bounding rectangle of the given set of nodes with
	 * or without the margins
	 * 
	 * @return
	 */
	public RectangleD calculateBounds(boolean isMarginIncluded,
			ArrayList<SbgnPDNode> nodes)
	{
		int boundLeft = Integer.MAX_VALUE;
		int boundRight = Integer.MIN_VALUE;
		int boundTop = Integer.MAX_VALUE;
		int boundBottom = Integer.MIN_VALUE;
		int nodeLeft;
		int nodeRight;
		int nodeTop;
		int nodeBottom;

		Iterator<SbgnPDNode> itr = nodes.iterator();

		while (itr.hasNext())
		{
			LNode lNode = itr.next();
			nodeLeft = (int) (lNode.getLeft());
			nodeRight = (int) (lNode.getRight());
			nodeTop = (int) (lNode.getTop());
			nodeBottom = (int) (lNode.getBottom());

			if (boundLeft > nodeLeft)
				boundLeft = nodeLeft;

			if (boundRight < nodeRight)
				boundRight = nodeRight;

			if (boundTop > nodeTop)
				boundTop = nodeTop;

			if (boundBottom < nodeBottom)
				boundBottom = nodeBottom;
		}

		if (isMarginIncluded)
		{
			return new RectangleD(boundLeft
					- SbgnPDConstants.COMPLEX_MEM_MARGIN, boundTop
					- SbgnPDConstants.COMPLEX_MEM_MARGIN, boundRight
					- boundLeft + 2 * SbgnPDConstants.COMPLEX_MEM_MARGIN,
					boundBottom - boundTop + 2
							* SbgnPDConstants.COMPLEX_MEM_MARGIN);
		}
		else
		{
			return new RectangleD(boundLeft, boundTop, boundRight - boundLeft,
					boundBottom - boundTop);
		}
	}

	/**
	 * calculates usedArea/totalArea inside the complexes and prints them out.
	 */
	private void calculateFullnessOfComplexes()
	{
		SbgnPDNode largestComplex = null;
		double totalArea = 0;
		double usedArea = 0;
		double maxArea = Double.MIN_VALUE;

		// find the largest complex -> area
		for (int i = 0; i < getAllNodes().length; i++)
		{
			SbgnPDNode s = (SbgnPDNode) getAllNodes()[i];
			if (s.type.equals(SbgnPDConstants.COMPLEX)
					&& s.getWidth() * s.getHeight() > maxArea)
			{
				maxArea = s.getWidth() * s.getHeight();
				largestComplex = s;
			}
		}

		usedArea = calculateUsedArea(largestComplex);

		totalArea = largestComplex.getWidth() * largestComplex.getHeight();

		if (compactionMethod == DefaultCompactionAlgorithm.TILING)
			System.out.println("Tiling results");
		else if (compactionMethod == DefaultCompactionAlgorithm.POLYOMINO_PACKING)
			System.out.println("Polyomino Packing results");

		// System.out.print(largestComplex.label +": ");
		System.out.println(" = " + usedArea / totalArea);
	}

	/**
	 * This method calculates the used area of a given complex node's children
	 */
	public double calculateUsedArea(SbgnPDNode parent)
	{
		int totalArea = 0;
		if (parent.getChild() == null)
			return 0.0;

		for (int i = 0; i < parent.getChild().getNodes().size(); i++)
		{
			SbgnPDNode node = (SbgnPDNode) parent.getChild().getNodes().get(i);

			if (!node.type.equalsIgnoreCase(SbgnPDConstants.COMPLEX))
			{
				totalArea += node.getWidth() * node.getHeight();
			}
			else
			{
				totalArea += calculateUsedArea(node);
			}
		}
		return totalArea;
	}

	public enum DefaultCompactionAlgorithm
	{
		TILING, POLYOMINO_PACKING
	};
}
