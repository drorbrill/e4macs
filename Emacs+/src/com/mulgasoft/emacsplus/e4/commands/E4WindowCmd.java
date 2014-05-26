/**
 * Copyright (c) 2009, 2014 Mark Feber, MulgaSoft
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.mulgasoft.emacsplus.e4.commands;

import static com.mulgasoft.emacsplus.EmacsPlusUtils.getPreferenceStore;
import static com.mulgasoft.emacsplus.preferences.PrefVars.ENABLE_SPLIT_SELF;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MArea;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainer;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainerElement;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.mulgasoft.emacsplus.EmacsPlusUtils;

/**
 * A base class for all split and join window commands (and their associates)
 * Use E4 injection
 *
 * @author mfeber - Initial API and implementation
 */
public abstract class E4WindowCmd {

	@Inject private EPartService partService;
	@Inject protected EModelService modelService;

	// split editor in two when true, else just rearrange editors in stack
	private static boolean splitSelf = EmacsPlusUtils.getPreferenceBoolean(ENABLE_SPLIT_SELF.getPref());
	
	static {
		// listen for changes in the property store
		getPreferenceStore().addPropertyChangeListener(
				new IPropertyChangeListener() {
					public void propertyChange(PropertyChangeEvent event) {
						if (ENABLE_SPLIT_SELF.getPref().equals(event.getProperty())) {
							E4WindowCmd.setSplitSelf((Boolean)event.getNewValue());
						}
					}
				}
		);
	}

	/**
	 * @param splitSelf the splitSelf to set
	 */
	public static void setSplitSelf(boolean splitSelf) {
		E4WindowCmd.splitSelf = splitSelf;
	}

	/**
	 * @return the splitSelf
	 */
	public boolean isSplitSelf() {
		return splitSelf;
	}

	/**
	 * Activate the cursor in the part
	 * @param apart
	 */
	protected void reactivate(MPart apart) {
		partService.activate(null);
		partService.activate(apart, true);
	}

	/**
	 * Simple cons
	 * @author mfeber - Initial API and implementation
	 */
	class PartAndStack {
		PartAndStack(MPart part, MElementContainer<MUIElement> stack) {
			this.part = part;
			this.stack = stack;
		}
		private MPart part;
		private MElementContainer<MUIElement> stack;
		public MPart getPart() {return part;}
		public MElementContainer<MUIElement> getStack() {return stack;}
	}
	
	/**
	 * The parent of the apart is typically a PartStack, but it could be something under an MCompositePart, so look up
	 * @param apart the original selection
	 * @return the MPart and PartStack
	 */
	protected PartAndStack getParentStack(MPart apart) {
		MPartSashContainerElement part = apart;
		MElementContainer<MUIElement> stack = apart.getParent();
		try {
			while (!((MPartSashContainerElement)stack instanceof MPartStack)) {
				if ((MPartSashContainerElement)stack instanceof MArea) {
					// some unexpected structure
					stack = null;
					part = apart;
					break;
				} else {
					part = (MPartSashContainerElement)stack;
					stack = stack.getParent();
				}
			}
		} catch (Exception e) {
			// Bail on anything unexpected - will just make the command a noop
			stack = null;
			part = apart;
		}
		return new PartAndStack((MPart)part, stack);
	}

	/**
	 * Get the ordered list of stacks
	 * @param apart
	 * @return a list of MElementContainer<MUIElement> representing all the PartStacks
	 */
	protected List<MElementContainer<MUIElement>> getOrderedStacks(MPart apart) {
		List<MElementContainer<MUIElement>> result = new ArrayList<MElementContainer<MUIElement>>();
		MElementContainer<MUIElement> parent = apart.getParent();
		// get the outer container
		while (!((MPartSashContainerElement)parent instanceof MArea)) {
			parent = parent.getParent();
		}
		// first part stack is the destination of all the others
		getStacks(result, parent);
		return result;
	}
	
	private void getStacks(List<MElementContainer<MUIElement>> result, MElementContainer<MUIElement> container) {
		for (MUIElement child : container.getChildren()) {
			@SuppressWarnings("unchecked") // We type check all the way down
			MElementContainer<MUIElement> c = (MElementContainer<MUIElement>)child;
			if (child instanceof MPartStack) {
				result.add(c);
			} else {
				getStacks(result,c);
			}
		}
	}
	
	/**
	 * Find the first stack with which we should join
	 * 
	 * @param dragStack the stack to join
	 * @return the target stack 
	 */
	@SuppressWarnings("unchecked")  // for safe cast to MElementContainer<MUIElement>
	protected MElementContainer<MUIElement> getAdjacentElement(MElementContainer<MUIElement> dragStack, boolean stackp) {
		MElementContainer<MUIElement> result = null;
		if (dragStack != null) {
			MElementContainer<MUIElement> psash = dragStack.getParent();
			// Trust but verify
			if ((MPartSashContainerElement)psash instanceof MPartSashContainer) {
				List<MUIElement> children = psash.getChildren();
				int size = children.size(); 
				if (size > 1) {
					int index = children.indexOf(dragStack)+1;
					result = (MElementContainer<MUIElement>)children.get((index == size) ? 0 : index);
					if (stackp) {
						result =  findNextStack(result);
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * If parent is a sash, keep looking until we find a PartStack
	 * 
	 * @param parent a sash of PartStack
	 * @return the first PartStack we find
	 */
	@SuppressWarnings("unchecked")  // for safe cast to MElementContainer<MUIElement>
	private MElementContainer<MUIElement> findNextStack(MElementContainer<MUIElement> parent) {
		MElementContainer<MUIElement> result = parent;
		if ((MPartSashContainerElement)parent instanceof MPartSashContainer) {
			List<MUIElement> children = parent.getChildren();
			result = findNextStack((MElementContainer<MUIElement>)children.get(0));
		}
		return result;
	}

}
