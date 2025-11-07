def define_env(env):
    """
    This is the hook for defining variables, macros and filters for the mkdocs-macros-plugin.
    See https://mkdocs-macros-plugin.readthedocs.io/en/latest/macros/.
    """

    branch_or_head = env.variables['git_branch']

    # Make those variables available to markdown docs
    env.variables['repo_filetree_url'] = f"{env.conf['repo_url']}/tree/{branch_or_head}"
    env.variables['examples_base_url'] = f"{env.variables['repo_filetree_url']}/examples"
