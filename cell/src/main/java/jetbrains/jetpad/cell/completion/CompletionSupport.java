/*
 * Copyright 2012-2016 JetBrains s.r.o
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
package jetbrains.jetpad.cell.completion;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import jetbrains.jetpad.base.*;
import jetbrains.jetpad.cell.*;
import jetbrains.jetpad.cell.event.CompletionEvent;
import jetbrains.jetpad.cell.event.FocusEvent;
import jetbrains.jetpad.cell.text.TextEditing;
import jetbrains.jetpad.cell.text.TextEditingTrait;
import jetbrains.jetpad.cell.trait.CellTrait;
import jetbrains.jetpad.cell.trait.CellTraitPropertySpec;
import jetbrains.jetpad.cell.util.Cells;
import jetbrains.jetpad.completion.*;
import jetbrains.jetpad.event.Event;
import jetbrains.jetpad.event.Key;
import jetbrains.jetpad.event.KeyEvent;
import jetbrains.jetpad.event.ModifierKey;
import jetbrains.jetpad.model.event.CompositeRegistration;
import jetbrains.jetpad.model.event.EventHandler;
import jetbrains.jetpad.model.property.*;

import java.util.Collections;

public class CompletionSupport {
  public static final CellTraitPropertySpec<TextCell> EDITOR = new CellTraitPropertySpec<>("textEditor");

  public static CellTrait trait() {
    return new CellTrait() {
      @Override
      public Object get(Cell cell, CellTraitPropertySpec<?> spec) {
        if (spec == Completion.COMPLETION_CONTROLLER) {
          return getCompletionHandler(cell);
        }
        return super.get(cell, spec);
      }

      private CompletionController getCompletionHandler(Cell cell) {
        return new CellCompletionController(cell);
      }

      @Override
      public void onComplete(final Cell cell, CompletionEvent event) {
        if (canComplete(cell)) {
          CompletionController controller = getCompletionHandler(cell);
          if (!controller.isActive() && controller.canActivate()) {
            controller.activate();
          }
          event.consume();
        }
        super.onComplete(cell, event);
      }

      private boolean canComplete(Cell cell) {
        Cell current = cell.getContainer().focusedCell.get();
        while (current != cell) {
          Cell parent = current.getParent();
          if (parent == null) {
            throw new IllegalStateException();
          }
          int index = parent.children().indexOf(current);
          if (index != 0) return false;
          current = parent;
        }
        return true;
      }
    };
  }

  static void showCompletion(final TextCell editor, Async<Iterable<CompletionItem>> items,
      final Runnable restoreCompletionState, final Runnable restoreFocusState) {

    if (!editor.focused().get()) {
      throw new IllegalArgumentException();
    }

    final CompletionMenuModel menuModel = new CompletionMenuModel();
    menuModel.loading.set(true);

    final CompositeRegistration completionReg = new CompositeRegistration();

    final ReadableProperty<String> prefixText = prefixText(editor);
    completionReg.add(PropertyBinding.bindOneWay(prefixText, menuModel.text));

    final Handler<CompletionItem> completer = new Handler<CompletionItem>() {
      @Override
      public void handle(CompletionItem item) {
        completionReg.remove();
        restoreFocusState.run();
        item.complete(prefixText.get()).run();
      }
    };

    final CompositeRegistration disposeMenuMapper = new CompositeRegistration();
    final Cell completionCell = CompletionMenu.createCell(menuModel, completer, disposeMenuMapper);

    completionReg.add(editor.focused().addHandler(new EventHandler<PropertyChangeEvent<Boolean>>() {
      @Override
      public void onEvent(PropertyChangeEvent<Boolean> event) {
        if (!event.getNewValue()) {
          completionReg.remove();
        }
      }
    }));
    completionReg.add(editor.addTrait(new CellTrait() {
      @Override
      public void onKeyPressed(Cell cell, KeyEvent event) {
        CompletionItem selectedItem = menuModel.selectedItem.get();

        if (event.is(Key.ESCAPE)) {
          completionReg.remove();
          restoreFocusState.run();
          event.consume();
          return;
        }

        if (selectedItem == null) return;

        if (event.is(Key.ENTER)) {
          completer.handle(selectedItem);
          event.consume();
          cell.dispatch(new Event(), Cells.AFTER_COMPLETED);
          return;
        }

        if (event.is(Key.UP)) {
          menuModel.up();
          event.consume();
          return;
        }

        if (event.is(Key.DOWN)) {
          menuModel.down();
          event.consume();
          return;
        }

        if (event.is(Key.PAGE_UP) || event.is(Key.PAGE_DOWN)) {
          int pageHeight = completionCell.getBounds().dimension.y / editor.dimension().y;
          for (int i = 0; i < pageHeight; i++) {
            if (event.is(Key.PAGE_DOWN)) {
              menuModel.down();
            } else {
              menuModel.up();
            }
          }
          event.consume();
        }
      }
    }));

    completionReg.add(new Registration() {
      @Override
      protected void doRemove() {
        completionCell.removeFromParent();
        disposeMenuMapper.remove();
        restoreCompletionState.run();
      }
    });

    items.onSuccess(new Handler<Iterable<CompletionItem>>() {
      @Override
      public void handle(Iterable<CompletionItem> items) {
        menuModel.loading.set(false);
        menuModel.items.addAll(FluentIterable.from(items).toList());
      }
    });
    items.onFailure(new Handler<Throwable>() {
      @Override
      public void handle(Throwable item) {
        menuModel.loading.set(false);
      }
    });

    editor.bottomPopup().set(completionCell);
    completionCell.scrollTo();
  }

  private static ReadableProperty<String> prefixText(final TextCell t) {
    return new DerivedProperty<String>(t.text(), t.caretPosition()) {
      @Override
      public String doGet() {
        return t.getPrefixText();
      }

      @Override
      public String getPropExpr() {
        return "prefixText(" + t + ")";
      }
    };
  }

  public static TextCell showSideTransformPopup(
      final Cell cell,
      final Property<Cell> targetPopup,
      final CompletionSupplier supplier,
      final boolean endRT) {

    final CellContainer container = cell.getContainer();
    final Value<Boolean> completed = new Value<>(false);
    final Value<Boolean> dismissed = new Value<>(false);
    final Runnable restoreState = container.saveState();

    final Function<CompletionItem, CompletionItem> wrap = new Function<CompletionItem, CompletionItem>() {
      @Override
      public CompletionItem apply(CompletionItem input) {
        return new WrapperCompletionItem(input) {
          @Override
          public Runnable complete(String text) {
            completed.set(true);
            return super.complete(text);
          }
        };
      }
    };

    final HorizontalCell popup = new HorizontalCell();
    final TextCell textCell = new TextCell();
    final Value<Handler<Boolean>> dismiss = new Value<>();
    textCell.focusable().set(true);
    final Registration traitReg = textCell.addTrait(new TextEditingTrait() {
      @Override
      public Object get(Cell cell, CellTraitPropertySpec<?> spec) {
        if (spec == Completion.COMPLETION) {
          return new CompletionSupplier() {
            @Override
            public Iterable<CompletionItem> get(CompletionParameters cp) {
              return FluentIterable.from(supplier.get(wrap(cp))).transform(wrap);
            }

            @Override
            public Async<Iterable<CompletionItem>> getAsync(CompletionParameters cp) {
              return Asyncs.map(supplier.getAsync(wrap(cp)), new Function<Iterable<CompletionItem>, Iterable<CompletionItem>>() {
                @Override
                public Iterable<CompletionItem> apply(Iterable<CompletionItem> input) {
                  return FluentIterable.from(input).transform(wrap);
                }
              });
            }
          };
        }

        return super.get(cell, spec);
      }

      @Override
      public void onPropertyChanged(Cell cell, CellPropertySpec<?> prop, PropertyChangeEvent<?> e) {
        if (prop == TextCell.TEXT) {
          PropertyChangeEvent<String> event = (PropertyChangeEvent<String>) e;
          if (Strings.isNullOrEmpty(event.getNewValue())) {
            dismiss.get().handle(false);
          }
        }

        super.onPropertyChanged(cell, prop, e);
      }

      @Override
      public void onKeyPressed(Cell cell, KeyEvent event) {
        if (event.is(Key.ESCAPE)) {
          dismiss.get().handle(false);
          event.consume();
          return;
        }

        super.onKeyPressed(cell, event);
      }

      @Override
      protected boolean onAfterType(TextCell editor) {
        if (super.onAfterType(editor)) return true;

        if (!editor.isEnd()) return false;

        String text = editor.text().get();

        final CompletionItems completion = new CompletionItems(supplier.get(wrap(CompletionParameters.EMPTY)));

        if (completion.hasSingleMatch(text, cell.get(TextEditing.EAGER_COMPLETION))) {
          completion.matches(text).get(0).complete(text).run();
          return true;
        }

        String prefix = text.substring(0, text.length() - 1);
        String suffix = text.substring(text.length() - 1);
        if (completion.matches(prefix).size() == 1 && completion.prefixedBy(text).isEmpty()) {
          completion.matches(prefix).get(0).complete(prefix).run();
          for (int i = 0; i < suffix.length(); i++) {
            container.keyTyped(new KeyEvent(Key.UNKNOWN, suffix.charAt(i), Collections.<ModifierKey>emptySet()));
          }
        }
        return true;
      }

      @Override
      public void onFocusLost(Cell cell, FocusEvent event) {
        super.onFocusLost(cell, event);
        dismiss.get().handle(true);
      }

      private CompletionParameters wrap(final CompletionParameters otherParams) {
        return new BaseCompletionParameters() {
          @Override
          public boolean isEndRightTransform() {
            return endRT;
          }

          @Override
          public boolean isMenu() {
            return otherParams.isMenu();
          }
        };
      }
    });

    popup.children().add(textCell);

    if (targetPopup.get() != null) {
      throw new IllegalStateException();
    }

    targetPopup.set(popup);
    textCell.focus();

    dismiss.set(new Handler<Boolean>() {
      @Override
      public void handle(Boolean focusLoss) {
        if (dismissed.get()) return;
        dismissed.set(true);
        popup.removeFromParent();
        traitReg.remove();
        if (!completed.get() && !focusLoss) {
          restoreState.run();
        }
      }
    });

    return textCell;
  }
}