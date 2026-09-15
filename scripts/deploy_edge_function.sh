#!/usr/bin/env bash
set -euo pipefail

# Deploys one Supabase Edge Function (supabase/functions/<name>) to the linked production
# project. Needed because `supabase db push` only ever pushes supabase/migrations/*.sql --
# Edge Functions are a separate deploy step that's easy to forget after adding a new function
# locally (delete-account shipped in code and passed review, but was never actually deployed to
# production until this was run for it manually).
#
# Usage:
#   ./scripts/deploy_edge_function.sh <function-name>
#
#   function-name   Required. Must match a directory under supabase/functions/.
#
# Requires the project to already be linked (`supabase link`) -- this deploys to whichever
# project supabase/.temp/project-ref currently points at, same as `supabase db push --linked`.

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <function-name>" >&2
  echo "Available functions:" >&2
  SCRIPT_DIR_USAGE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  ls "$SCRIPT_DIR_USAGE/../supabase/functions" >&2
  exit 1
fi

FUNCTION_NAME="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

FUNCTION_DIR="supabase/functions/$FUNCTION_NAME"
if [[ ! -d "$FUNCTION_DIR" ]]; then
  echo "No such function: $FUNCTION_DIR does not exist" >&2
  exit 1
fi

echo "==> Deploying '$FUNCTION_NAME' to the linked Supabase project..."
supabase functions deploy "$FUNCTION_NAME"

echo "==> Deployed functions now live on the project:"
supabase functions list

echo "==> Done."
