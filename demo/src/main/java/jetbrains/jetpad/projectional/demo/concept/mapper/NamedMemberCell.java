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
package jetbrains.jetpad.projectional.demo.concept.mapper;

import jetbrains.jetpad.projectional.cell.*;
import jetbrains.jetpad.projectional.cell.indent.IndentCell;
import jetbrains.jetpad.projectional.cell.action.CellActions;
import jetbrains.jetpad.projectional.cell.util.CellFactory;
import jetbrains.jetpad.projectional.cell.support.ProjectionalSynchronizers;
import jetbrains.jetpad.projectional.cell.support.TextEditing;
import jetbrains.jetpad.projectional.cell.trait.BaseCellTrait;
import jetbrains.jetpad.projectional.cell.trait.CellTraitPropertySpec;
import jetbrains.jetpad.projectional.util.Validators;

import static jetbrains.jetpad.projectional.cell.util.CellFactory.placeHolder;
import static jetbrains.jetpad.projectional.cell.util.CellFactory.space;

class NamedMemberCell extends IndentCell {
  final TextCell name = new TextCell();

  NamedMemberCell(String label) {
    TextCell labelView = new TextCell();
    labelView.text().set(label);
    labelView.addTrait(TextEditing.validTextEditing(Validators.equalsTo(label)));
    CellFactory.to(this, labelView, space(), name, placeHolder(name, "<no name>"));
    name.addTrait(TextEditing.validTextEditing(Validators.identifier()));
    focusable().set(true);

    addTrait(new BaseCellTrait() {
      @Override
      public Object get(Cell cell, CellTraitPropertySpec<?> spec) {
        if (spec == ProjectionalSynchronizers.ON_CREATE) {
          return CellActions.toEnd(name);
        }

        return super.get(cell, spec);
      }
    });
  }
}