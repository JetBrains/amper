#!/usr/bin/env python3
import os
import re
import shutil
import sys

def print_header(text):
    print(f"\n{'-'*50}\n{text}\n{'-'*50}")

def copy_and_rebrand_wrappers(repo_root):
    print_header("Step 1: Generating Kargo Wrappers from Amper Wrappers")
    wrappers = [
        ('amper', 'kargo'),
        ('amper.bat', 'kargo.bat'),
        ('amper-from-sources.bat', 'kargo-from-sources.bat'),
        ('sources/amper-wrapper/resources/wrappers/amper.template.sh', 'sources/amper-wrapper/resources/wrappers/kargo.template.sh'),
        ('sources/amper-wrapper/resources/wrappers/amper.template.bat', 'sources/amper-wrapper/resources/wrappers/kargo.template.bat')
    ]
    
    for amper_name, kargo_name in wrappers:
        amper_path = os.path.join(repo_root, amper_name)
        kargo_path = os.path.join(repo_root, kargo_name)
        
        if not os.path.exists(amper_path):
            print(f"  [Warning] {amper_name} not found, skipping {kargo_name}")
            continue
            
        # Copy file
        shutil.copy2(amper_path, kargo_path)
        
        # Make the kargo wrappers executable
        if not kargo_name.endswith('.bat'):
            os.chmod(kargo_path, 0o755)
            
        # Rebrand contents in memory
        with open(kargo_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        old_content = content
        
        # High impact public strings in wrappers
        content = content.replace("Amper CLI", "Kargo CLI")
        content = content.replace("Welcome to Amper", "Welcome to Kargo")
        content = content.replace("Amper distribution", "Kargo distribution")
        content = content.replace("Amper project", "Kargo project")
        content = content.replace("./amper", "./kargo")
        
        # ASCII Art Block Substitution
        amper_ascii = r'''      echo '        _____  Welcome to                                  '
      echo '       /:::::|  ____   ___     ____      ____    __  ___   '
      echo '      /::/|::| |::::\_|:::\   |:::::\   /::::\  |::|/:::|  '
      echo '     /::/ |::| |::|\:::|\::\  |::|\::\ /:/__\:\ |:::/      '
      echo '    /::/__|::| |::| |::| |::| |::| |::|:::::::/ |::|       '
      echo '   /:::::::::| |::| |::| |::| |::|/::/ \::\__   |::|       '
      echo '  /::/    |::| |::| |::| |::| |:::::/   \::::|  |::|       '
      echo '                              |::|                         '
      echo "                              |::|  v.$amper_version       "'''

        kargo_ascii = r'''      echo '                                       Welcome to'
      echo -n '██╗  ██╗ '; sleep 0.02
      echo -n '█████╗ '; sleep 0.02
      echo -n '██████╗  '; sleep 0.02
      echo -n '██████╗  '; sleep 0.02
      echo '██████╗ '; sleep 0.05
      
      echo -n '██║ ██╔╝'; sleep 0.02
      echo -n '██╔══██╗'; sleep 0.02
      echo -n '██╔══██╗'; sleep 0.02
      echo -n '██╔════╝ '; sleep 0.02
      echo '██╔═══██╗'; sleep 0.05
      
      echo -n '█████╔╝ '; sleep 0.02
      echo -n '███████║'; sleep 0.02
      echo -n '██████╔╝'; sleep 0.02
      echo -n '██║  ███╗'; sleep 0.02
      echo '██║   ██║'; sleep 0.05
      
      echo -n '██╔═██╗ '; sleep 0.02
      echo -n '██╔══██║'; sleep 0.02
      echo -n '██╔══██╗'; sleep 0.02
      echo -n '██║   ██║'; sleep 0.02
      echo '██║   ██║'; sleep 0.05
      
      echo -n '██║  ██╗'; sleep 0.02
      echo -n '██║  ██║'; sleep 0.02
      echo -n '██║  ██║'; sleep 0.02
      echo -n '╚██████╔╝'; sleep 0.02
      echo '╚██████╔╝'; sleep 0.05
      
      echo -n '╚═╝  ╚═╝'; sleep 0.02
      echo -n '╚═╝  ╚═╝'; sleep 0.02
      echo -n '╚═╝  ╚═╝'; sleep 0.02
      echo -n ' ╚═════╝ '; sleep 0.02
      echo ' ╚═════╝ '; sleep 0.2
      
      echo ""
      echo "   v.$amper_version"'''
      
        content = content.replace(amper_ascii, kargo_ascii)
        
        # GitHub Releases URL Rebranding
        content = content.replace("https://packages.jetbrains.team/maven/p/amper/amper", "https://github.com/leodouglas/kargo-build/releases/download")
        if kargo_name.endswith('.bat'):
            content = content.replace("%AMPER_DOWNLOAD_ROOT%/org/jetbrains/amper/amper-cli/%amper_version%/amper-cli-%amper_version%-dist.tgz", "%AMPER_DOWNLOAD_ROOT%/%amper_version%/amper-cli-%amper_version%-dist.tgz")
        else:
            content = content.replace("$AMPER_DOWNLOAD_ROOT/org/jetbrains/amper/amper-cli/$amper_version/amper-cli-$amper_version-dist.tgz", "$AMPER_DOWNLOAD_ROOT/$amper_version/amper-cli-$amper_version-dist.tgz")

        
        if content != old_content:
            with open(kargo_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  [Rebranded] {kargo_name}")
        else:
            print(f"  [Copied] {kargo_name} (No brand changes needed)")

def rebrand_cli_strings(repo_root):
    print_header("Step 2: Rebranding CLI Outputs (Strict RegExp)")
    cli_dir = os.path.join(repo_root, 'sources/amper-cli/src/org/jetbrains/amper/cli')
    wrapper_templates_dir = os.path.join(repo_root, 'sources/amper-wrapper/resources/wrappers')
    
    def process_cli_file(filepath):
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
                
            old_content = content
            
            # Wrapper templates generation logic is now handled in Step 1
            if filepath.endswith('.kt'):
                opts = re.MULTILINE | re.DOTALL
                
                # Replace inside `help = "..."` or `override fun help(context: Context): String = "..."`
                content = re.sub(r'(help\s*=\s*"[^"]*)Amper([^"]*")', r'\1Kargo\2', content)
                content = re.sub(r'(override fun help[^{]*=\s*"[^"]*)Amper([^"]*")', r'\1Kargo\2', content)
                
                # error messages `userReadableError("...Amper...")`
                content = re.sub(r'(userReadableError\([^)]*")([^"]*)Amper([^"]*)("\))', r'\1\2Kargo\3\4', content, flags=opts)
                
                # prints `terminal.print("...Amper...")`
                content = re.sub(r'(terminal\.print[^)]*")([^"]*)Amper([^"]*)("\))', r'\1\2Kargo\3\4', content, flags=opts)
                
                # CLI command root names
                content = content.replace('name = "amper"', 'name = "kargo"')
                
                # Specific known strings
                content = content.replace('"JetBrains Amper version', '"Kargo version')
                content = content.replace('Amper command for', 'Kargo command for')
                
            if content != old_content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"  [Rebranded] {os.path.relpath(filepath, repo_root)}")
                
        except Exception as e:
            print(f"  [Error] Processing {filepath}: {e}")

    for search_dir in [cli_dir, wrapper_templates_dir]:
        if not os.path.exists(search_dir):
            continue
        for root, _, files in os.walk(search_dir):
            for file in files:
                if file.endswith('.kt') or file.endswith('.sh') or file.endswith('.bat'):
                    process_cli_file(os.path.join(root, file))

def rebrand_documentation(repo_root):
    print_header("Step 3: Rebranding Documentation Files")
    docs_dir = os.path.join(repo_root, 'docs')
    
    if not os.path.exists(docs_dir):
        print(f"  [Warning] Docs directory not found at {docs_dir}")
        return
        
    for root, _, files in os.walk(docs_dir):
        for file in files:
            if file.endswith('.md') or file.endswith('.yml') or file.endswith('.html'):
                filepath = os.path.join(root, file)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        content = f.read()
                        
                    old_content = content
                    
                    # Word boundaries approach to avoid urls like `amper.jetbrains.com` being randomly spliced
                    content = re.sub(r'\bAmper\b', 'Kargo', content)
                    content = re.sub(r'\bJetBrains Amper\b', 'Kargo', content)
                    
                    # Manual deterministic replacements
                    content = content.replace('amper.org', 'kargo.build')
                    content = content.replace('amper.dev', 'kargo.build')
                    content = content.replace('https://github.com/JetBrains/amper', 'https://github.com/kargo-build/kargo')
                    
                    # mkdocs internal refs
                    if file == "mkdocs.yml":
                        content = content.replace('./amper', './kargo')
                    
                    if content != old_content:
                        with open(filepath, 'w', encoding='utf-8') as f:
                            f.write(content)
                        print(f"  [Rebranded] {os.path.relpath(filepath, repo_root)}")
                        
                except Exception as e:
                    print(f"  [Error] Processing {filepath}: {e}")

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    
    print(f"Starting Kargo Synchronization Tool...")
    print(f"Repository Root: {repo_root}")
    
    copy_and_rebrand_wrappers(repo_root)
    rebrand_cli_strings(repo_root)
    rebrand_documentation(repo_root)
    
    print("\n✅ Synchronization and Rebranding Complete!")
    print("Please review the changes via `git diff` before committing.")

if __name__ == "__main__":
    main()
