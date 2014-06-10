define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/query',
    'dojo/html',
    'dojo/date/locale',
    'dojo/request',
    'dojo/json',
    'dojo/store/Memory',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/Tree',
    'dijit/tree/ObjectStoreModel',
    'dijit/Editor',
    'dijit/Menu',
    'dijit/MenuItem',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'put-selector/put',
    'dojo/domReady!'
], function(declare, lang, config, array, query, html, date, request, json, Memory, JsonRest, Observable,
            registry, ContentPane, Tree, ObjectStoreModel, Editor, Menu, MenuItem,
            Grid, OnDemandGrid, Selection, ColumnResizer, put) {

    var QueryEditor = declare(null, {

        constructor: function() {
            var self = this;
            self.initTaskStatus();
            self.initDocument();
            self.initMetadata();
        },

        initTaskStatus: function() {
            var self = this;

            // store
            self.taskStore = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/task/'
            }));

            // grid
            var CustomGrid = declare([OnDemandGrid, Selection]);
            self.grid = new CustomGrid({
                className: 'dgrid-autoheight grid-task-status',
                sort: [{attribute: 'id', descending: true}],
                store: self.taskStore,
                columns: [
                    {label: 'ID', field: 'id', sortable: false},
                    {label: '查询语句', field: 'queriesBrief', sortable: false},
                    {label: '创建时间', field: 'created', sortable: false},
                    {label: '状态', field: 'status', sortable: false},
                    {label: '运行时间', field: 'duration', sortable: false}
                ],
                selectionMode: 'single',
                renderRow: function(object, options) {
                    var div = put('div.collapsed', CustomGrid.prototype.renderRow.apply(this, arguments)),
                        expando = put(div, 'div.expando', {innerHTML: object.queries.replace(/\n/g, '<br>')});
                    return div;
                }
            }, 'gridTaskStatus');

            // https://github.com/SitePen/dgrid/blob/v0.3.15/demos/multiview/multiview.js
            var expandedNode;
            self.grid.on('.dgrid-row:click', function(evt) {
                var node = self.grid.row(evt).element,
                    collapsed = node.className.indexOf('collapsed') >= 0;
                put(node, (collapsed ? '!' : '.') + 'collapsed');
                collapsed && expandedNode && put(expandedNode, '.collapsed');
                expandedNode = collapsed ? node : null;
            });

            // context menu
            function getSelectedTask() {
                var task = null;
                for (var id in self.grid.selection) {
                    if (self.grid.selection[id]) {
                        task = self.grid.row(id).data;
                        break;
                    }
                }
                return task;
            }

            var menu = new Menu({
                targetNodeIds: [self.grid.domNode]
            });
            menu.addChild(new MenuItem({
                label: '查看结果',
                onClick: function() {
                    self.showResult(getSelectedTask());
                }
            }));
            menu.addChild(new MenuItem({
                label: '取消任务'
            }));
            menu.addChild(new MenuItem({
                label: '删除任务'
            }));

            var updated = null;

            setInterval(function() {

                if (updated == null) {
                    query('.dgrid-row', self.grid.domNode).forEach(function(node) {
                        var task = self.grid.row(node).data;
                        if (updated == null || task.updated > updated) {
                            updated = task.updated;
                        }
                    });
                }

                var data = {};
                if (updated != null) {
                    data['updated'] = updated;
                }

                self.taskStore.query(data).forEach(function(task) {

                    self.taskStore.notify(task, task.id);

                    if (updated == null || task.updated > updated) {
                        updated = task.updated;
                    }

                    self.showResult(task);
                });

            }, 3000);

        },

        showResult: function(task) {
            var self = this;

            var showError;

            switch (task.status) {
            case '运行成功':
                showError = false;
                break;
            case '运行失败':
                showError = true;
                break;
            default:
                return;
            }

            var pane = new ContentPane({
                title: '运行结果[' + task.id + ']',
                closable: true
            });

            var gridStatus = new Grid({
                className: 'dgrid-autoheight grid-task-status',
                columns: [
                    {label: 'ID', field: 'id', sortable: false},
                    {label: '查询语句', field: 'queriesBrief', sortable: false},
                    {label: '创建时间', field: 'created', sortable: false},
                    {label: '状态', field: 'status', sortable: false},
                    {label: '运行时间', field: 'duration', sortable: false}
                ],
                renderRow: function(object, options) {
                    var div = put('div.collapsed', Grid.prototype.renderRow.apply(this, arguments)),
                        expando = put(div, 'div.expando', {innerHTML: object.queries.replace(/\n/g, '<br>')});
                    return div;
                }
            });

            gridStatus.on('.dgrid-row:click', function(evt) {
                var node = gridStatus.row(evt).element,
                    collapsed = node.className.indexOf('collapsed') >= 0;
                put(node, (collapsed ? '!' : '.') + 'collapsed');
            });

            gridStatus.renderArray([task]);

            pane.addChild(gridStatus);

            var bottomCol = registry.byId('bottomCol');
            bottomCol.addChild(pane);
            bottomCol.selectChild(pane);

            if (showError) {

                request(config.contextPath + '/query-editor/api/task/error/' + task.id).then(function(data) {
                    put(pane.domNode, 'div.task-error-header', '错误日志');
                    put(pane.domNode, 'pre', data);
                });

            } else {

                request(config.contextPath + '/query-editor/api/task/output/' + task.id).then(function(data) {

                    if (!data) {
                        put(pane.domNode, 'div.task-error-header', '未返回结果');
                        return;
                    }

                    var lines = data.split(/\n/);
                    var columns = [];
                    array.forEach(lines.shift().split(/\t/), function(column) {
                         columns.push({
                             label: column,
                             field: column
                         });
                    });

                    var data = [];
                    array.forEach(lines, function(line, id) {
                        if (!line) {
                            return;
                        }
                        var row = {};
                        array.forEach(line.split(/\t/), function(columnData, columnIndex) {
                            if (columnIndex > columns.length - 1) {
                                return;
                            }
                            row[columns[columnIndex].field] = columnData;
                        });
                        data.push(row);
                    });

                    console.log(data);

                    var gridOutput = new (declare([OnDemandGrid, ColumnResizer]))({
                        store: new Memory({data: data}),
                        columns: columns
                    });

                    pane.addChild(gridOutput);
                });

            }
        },

        submitTask: function(queries) {
            var self = this;

            queries = lang.trim(queries);
            if (!queries) {
                alert('Queries cannot be empty.');
                return false;
            }

            self.taskStore.add({
                queries: queries
            });

        },

        initDocument: function() {
            var self = this;

            var store = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/doc/',
                getChildren: function(object) {
                    if (object.children !== true) {
                        return object.children;
                    }
                    return this.get(object.id).then(function(fullObject) {
                        return fullObject.children;
                    });
                }
            }));

            var model = new ObjectStoreModel({
                store: store,
                getRoot: function(onItem) {
                    this.store.get('root').then(onItem);
                },
                mayHaveChildren: function(item) {
                    return item.children !== false;
                }
            });

            self.treeDoc = new Tree({
                model: model,
                onDblClick: function(item) {

                    if (item.children !== false) {
                        return;
                    }

                    var central = registry.byId('central');
                    var paneId = 'editorPane_' + item.id;
                    var pane = registry.byId(paneId);

                    if (typeof pane != 'undefined') {
                        central.selectChild(pane);
                        return;
                    }

                    this.model.store.get(item.id).then(function(object) {

                        var pane = new ContentPane({
                            id: paneId,
                            title: object.name,
                            closable: true
                        });

                        var editor = new Editor({
                            plugins: ['undo', 'redo', '|', 'cut', 'copy', 'paste', '|', 'runner'],
                            value: object.content
                        });

                        pane.addChild(editor);
                        central.addChild(pane);
                        central.selectChild(pane);
                    });
                }
            }, 'treeDoc');

            // context menu
            var menu = new Menu({
                targetNodeIds: [self.treeDoc.domNode],
                selector: '.dijitTreeNode'
            });

            menu.addChild(new MenuItem({
                label: '新建文件',
                onClick: function() {

                    var name = prompt('请输入文件名');
                    if (!name) {
                        return;
                    }

                    var item = registry.byNode(this.getParent().currentTarget).item;

                    store.add({
                        parent: item.children !== false ? item.id: item.parent,
                        filename: name,
                        content: '',
                        isFolder: false
                    });
                }
            }));

            menu.addChild(new MenuItem({
                label: '重命名',
                onClick: function() {
                    var name = prompt('请输入新的文件名');
                    if (!name) {
                        return;
                    }

                    var item = registry.byNode(this.getParent().currentTarget).item;

                    store.put({
                        id: item.id,
                        filename: name
                    });
                }
            }));

            menu.addChild(new MenuItem({
                label: '删除',
                onClick: function() {
                    if (!confirm('确定要删除吗？')) {
                        return;
                    }
                    var item = registry.byNode(this.getParent().currentTarget).item;
                    store.remove(item.id);
                }
            }));
        },

        initMetadata: function() {
            var self = this;

            var store = new JsonRest({
                target: config.contextPath + '/query-editor/api/metadata/',
                getChildren: function(object) {
                    if (object.children !== true) {
                        return object.children;
                    }
                    return this.get(object.id).then(function(fullObject) {
                        return fullObject.children;
                    });
                }
            });

            var model = new ObjectStoreModel({
                store: store,
                getRoot: function(onItem) {
                    this.store.get('root').then(onItem);
                },
                mayHaveChildren: function(item) {
                    return 'children' in item;
                }
            });

            self.treeMetadata = new Tree({
                model: model
            }, 'treeMetadata');
        },

        _theEnd: undefined

    });

    return new QueryEditor();

});
