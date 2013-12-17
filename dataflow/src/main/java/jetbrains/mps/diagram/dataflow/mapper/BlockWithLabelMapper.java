/*
 * Copyright 2012-2013 JetBrains s.r.o
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.mps.diagram.dataflow.mapper;

import jetbrains.jetpad.cell.util.RootController;
import jetbrains.jetpad.mapper.Synchronizers;
import jetbrains.jetpad.cell.TextCell;
import jetbrains.jetpad.cell.text.TextEditing;
import jetbrains.jetpad.cell.view.CellView;
import jetbrains.jetpad.projectional.view.GroupView;
import jetbrains.mps.diagram.dataflow.model.Block;

public class BlockWithLabelMapper extends BlockMapper {
  private CellView myCell;

  public BlockWithLabelMapper(Block source) {
    super(source);
    myCell = new CellView(new GroupView());
    RootController.install(myCell.container);


    TextCell cell = new TextCell();
    cell.addTrait(TextEditing.textEditing());
    myCell.cell.set(cell);

    getTarget().contentView.children().add(myCell);
  }

  @Override
  protected void registerSynchronizers(SynchronizersConfiguration conf) {
    super.registerSynchronizers(conf);

    conf.add(Synchronizers.forProperties(getSource().text, ((TextCell) myCell.cell.get()).text()));
    conf.add(Synchronizers.forProperty(getTarget().rect.background(), myCell.background()));
  }
}