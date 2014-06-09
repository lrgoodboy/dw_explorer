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
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/Tree',
    'dijit/tree/ObjectStoreModel',
    'dijit/Editor',
    'dijit/Menu',
    'dijit/MenuItem',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'put-selector/put',
    'dojo/domReady!'
], function(declare, lang, config, array, query, html, date, request, json, JsonRest, Observable,
            registry, ContentPane, Tree, ObjectStoreModel, Editor, Menu, MenuItem,
            OnDemandGrid, Selection, put) {

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
                className: 'dgrid-autoheight',
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

            if (task.status == '运行成功') {
                request(config.contextPath + '/query-editor/api/task/output/' + task.id).then(function(data) {
                    var pane = new ContentPane({
                        title: '查询结果[' + task.id + ']',
                        content: data,
                        closable: true
                    });
                    registry.byId('bottomCol').addChild(pane);
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

            var store = new JsonRest({
                target: config.contextPath + '/query-editor/api/doc/',
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
